/* DKC1Recomp Android frontend, v1.
 * This host calls the real upstream game/runtime. It contains no replacement
 * emulator, game stubs, generated cartridge code, or bundled ROM data.
 * All game, audio producer, snapshot and SRAM operations run on SDL_main's
 * single thread. JNI and SDL event watches publish atomic requests only.
 */
#define _POSIX_C_SOURCE 200809L
#include "android_platform.h"
#include "dkc1_game.h"
#include "dkc1_video.h"
#include "verified_rom.h"
#include "content_mod.h"
#include "native_module.h"
#include "addon_runtime.h"
#include "mod_media.h"
#include "desktop_audio_rate.h"
#include "desktop_filter.h"
#include "dkc1_msu1.h"
#include "dkc1_haptics.h"
#include "desktop_rewind.h"
#include "common_cpu_infra.h"
#include "common_rtl.h"
#include "snes/interp_bridge.h"
#include "audio_trace.h"
#include <SDL.h>
#include <SDL_system.h>
#include <android/log.h>
#include <jni.h>
#include <errno.h>
#include <limits.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define TAG "DKC1Recomp"
enum { CMD_SAVE=1, CMD_LOAD=2, CMD_QUIT=4, CMD_SRAM=8, CMD_REWIND=16 };
enum { AUDIO_RATE=32040, AUDIO_CHANNELS=2, AUDIO_CAPACITY=1024 };
static atomic_uint s_touch, s_requests, s_user_command;
enum { OPT_VOLUME,OPT_SAMPLING,OPT_PALETTE,OPT_SCANLINES,OPT_SLOT,OPT_DEADZONE,
       OPT_PAD_MAPPING,OPT_AUTOSAVE,OPT_EDGE,OPT_MUTED,OPT_REWIND,OPT_SPEED,OPT_HAPTICS,OPT_COUNT };
static atomic_int s_options[OPT_COUNT]={100,0,0,0,0,24,0,1,3,0,0,0,1};
static atomic_int s_options_dirty;
static uint8_t s_filtered[448*224*4];
static Dkc1DesktopColorFilter s_color_filter;
static int s_palette=-1, s_sampling=-1;
static const char *s_aspect_key="4x3";
static uint64_t s_last_auto_frame=UINT64_MAX;
static int ClampOption(int v,int lo,int hi){return v<lo?lo:v>hi?hi:v;}
JNIEXPORT void JNICALL Java_com_ylports_dkc1recomp_GameActivity_nativeConfigure
(JNIEnv *env,jclass cls,jintArray options) {
  (void)cls;if(!options||(*env)->GetArrayLength(env,options)!=OPT_COUNT)return;
  jint values[OPT_COUNT];(*env)->GetIntArrayRegion(env,options,0,OPT_COUNT,values);
  if((*env)->ExceptionCheck(env))return;
  const int lo[OPT_COUNT]={0,0,0,0,0,5,0,0,0,0,0,0,0};
  const int hi[OPT_COUNT]={100,2,3,60,4,50,1,1,3,1,1,1,1};
  for(int i=0;i<OPT_COUNT;i++)atomic_store(&s_options[i],ClampOption(values[i],lo[i],hi[i]));
  atomic_store(&s_options_dirty,1);
}
static atomic_int s_menu_paused, s_lifecycle_paused, s_os_background, s_muted;
static SDL_Window *s_window;
static SDL_Renderer *s_renderer;
static SDL_Texture *s_texture, *s_sharp;
static int s_sharp_scale;
static Dkc1Msu1 *s_msu1;
static Dkc1RewindHistory s_rewind;
static uint8_t *s_rewind_scratch;
static size_t s_rewind_capacity;
static int s_rewind_blocked;
static SDL_AudioDeviceID s_audio;
static SDL_GameController *s_pads[2];
static jobject s_activity;
static jmethodID s_notice_method, s_menu_method, s_stomp_method;
static int s_running=1, s_core_ready, s_width, s_need_texture=1, s_ready_for_present;
static uint8_t s_pixels[kDkc1VideoMaxWidth*kDkc1VideoHeight*4];
static uint8_t *s_last_sram;
static size_t s_last_sram_size;
static char s_sram_name[80]="dkc1.srm";
static int s_sram_persisted, s_sram_writable=1, s_failed;

static uint64_t s_frame;
static Dkc1StompProbe s_stomp_probe;
static int s_audio_started, s_audio_waiting=1;
static unsigned s_audio_threshold=2136;
static double s_audio_accumulator, s_audio_average=-1, s_audio_target=1604;
static Dkc1AudioStretch s_stretch;
static int16_t s_audio_in[AUDIO_CAPACITY*2], s_audio_out[(AUDIO_CAPACITY+16)*2];

JNIEXPORT void JNICALL Java_com_ylports_dkc1recomp_GameActivity_nativeSetTouchMask
(JNIEnv *env,jclass cls,jint mask) {
  (void)env;(void)cls;atomic_store(&s_touch, (unsigned)mask & 0xfffu);
}
JNIEXPORT void JNICALL Java_com_ylports_dkc1recomp_GameActivity_nativeSetMenuPaused
(JNIEnv *env,jclass cls,jboolean paused) {
  (void)env;(void)cls;atomic_store(&s_menu_paused,paused?1:0);
  if(paused)atomic_store(&s_touch,0);
}
JNIEXPORT void JNICALL Java_com_ylports_dkc1recomp_GameActivity_nativeSetLifecyclePaused
(JNIEnv *env,jclass cls,jboolean paused) {
  (void)env;(void)cls;atomic_store(&s_lifecycle_paused,paused?1:0);
  if(paused){atomic_store(&s_touch,0);atomic_fetch_or(&s_requests,CMD_SRAM);}
}
JNIEXPORT void JNICALL Java_com_ylports_dkc1recomp_GameActivity_nativeRequest
(JNIEnv *env,jclass cls,jint command) {
  (void)env;(void)cls;
  if(command&CMD_QUIT)atomic_fetch_or(&s_requests,CMD_QUIT);
  unsigned expected=0,request=(unsigned)command&19u;
  if(request){request|=(unsigned)atomic_load(&s_options[OPT_SLOT])<<8;
    (void)atomic_compare_exchange_strong(&s_user_command,&expected,request);}

}
JNIEXPORT void JNICALL Java_com_ylports_dkc1recomp_GameActivity_nativeSetMuted
(JNIEnv *env,jclass cls,jboolean muted) {
  (void)env;(void)cls;atomic_store(&s_muted,muted?1:0);
}
static JNIEnv *JavaEnv(void) { return (JNIEnv *)SDL_AndroidGetJNIEnv(); }
static void ClearJavaException(JNIEnv *env) {
  if((*env)->ExceptionCheck(env)){
    (*env)->ExceptionClear(env);
    __android_log_write(ANDROID_LOG_WARN,TAG,"Java callback failed");
  }
}
static void BindJava(void) {
  JNIEnv *env=JavaEnv();if(!env)return;
  jobject local=(jobject)SDL_AndroidGetActivity();if(!local)return;
  s_activity=(*env)->NewGlobalRef(env,local);
  jclass cls=(*env)->GetObjectClass(env,local);
  if(cls){
    s_notice_method=(*env)->GetMethodID(env,cls,"onNativeStatus","(Ljava/lang/String;)V");
    ClearJavaException(env);
    s_menu_method=(*env)->GetMethodID(env,cls,"openPauseFromNative","()V");
    ClearJavaException(env);
    s_stomp_method=(*env)->GetMethodID(env,cls,"onNativeStomp","()V");
    ClearJavaException(env);(*env)->DeleteLocalRef(env,cls);
  }
  (*env)->DeleteLocalRef(env,local);
}
static void Notice(const char *message) {
  fprintf(stderr,"[android] %s\n",message);
  __android_log_write(ANDROID_LOG_INFO,TAG,message);
  JNIEnv *env=JavaEnv();if(!env||!s_activity||!s_notice_method)return;
  jstring text=(*env)->NewStringUTF(env,message);
  if(text){(*env)->CallVoidMethod(env,s_activity,s_notice_method,text);(*env)->DeleteLocalRef(env,text);}
  ClearJavaException(env);
}
static void StompHaptic(void) {
  if(!atomic_load(&s_options[OPT_HAPTICS]))return;
  JNIEnv *env=JavaEnv();
  if(env&&s_activity&&s_stomp_method){(*env)->CallVoidMethod(env,s_activity,s_stomp_method);ClearJavaException(env);}
}
static void OpenMenu(void) {
  if(atomic_exchange(&s_menu_paused,1))return;
  atomic_store(&s_touch,0);
  JNIEnv *env=JavaEnv();
  if(env&&s_activity&&s_menu_method){
    (*env)->CallVoidMethod(env,s_activity,s_menu_method);ClearJavaException(env);
  }else atomic_store(&s_menu_paused,0);
}
static void Failure(const char *message) {
  s_failed=1;
  fprintf(stderr,"[android-fatal] %s\n",message);
  __android_log_write(ANDROID_LOG_ERROR,TAG,message);
  (void)Dkc1AndroidAtomicWrite("last-error.txt",message,strlen(message));
  s_running=0;
}
static int Background(void) {
  return atomic_load(&s_lifecycle_paused)||atomic_load(&s_os_background);
}
static int Paused(void) { return Background()||atomic_load(&s_menu_paused); }
static int SDLCALL EventWatch(void *userdata, SDL_Event *event) {
  (void)userdata;
  if(event->type==SDL_APP_WILLENTERBACKGROUND){
    atomic_store(&s_os_background,1);atomic_store(&s_touch,0);
    atomic_fetch_or(&s_requests,CMD_SRAM);
  }else if(event->type==SDL_APP_DIDENTERFOREGROUND){
    atomic_store(&s_os_background,0);
  }
  return 1;
}
static int SaveSram(void) {
  if(s_failed||!s_sram_writable)return 0;
  if(!s_core_ready||!g_sram||g_sram_size<=0)return 1;
  size_t size=(size_t)g_sram_size;
  if(s_last_sram_size!=size){
    uint8_t *next=realloc(s_last_sram,size);if(!next)return 0;
    s_last_sram=next;s_last_sram_size=size;s_sram_persisted=0;
  }
  if(s_sram_persisted&&!memcmp(s_last_sram,g_sram,size))return 1;
  if(!Dkc1AndroidAtomicWrite(s_sram_name,g_sram,size))return 0;
  memcpy(s_last_sram,g_sram,size);s_sram_persisted=1;
  return 1;
}
static void LoadSram(void) {
  if(!g_sram||g_sram_size<=0||access(s_sram_name,F_OK)!=0)return;
  if(!Dkc1AndroidReadExact(s_sram_name,g_sram,(size_t)g_sram_size)){
    char backup[PATH_MAX];
    if(Dkc1AndroidQuarantine(s_sram_name,backup,sizeof backup)){
      fprintf(stderr,"[save] invalid SRAM preserved as %s\n",backup);
      Notice("La SRAM no era valida. Se conserva una copia .invalid; no se ha cargado.");
    }else{
      s_sram_writable=0;
      Failure("No se pudo preservar la SRAM ilegible. Se ha detenido el juego sin sobrescribirla.");
    }
  }
}
static int SaveSnapshotTo(const char *path) {
  /* Upstream writes its actual v8-compatible native snapshot. Rename only
   * after a complete successful write, preserving the previous quicksave. */
  char temporary[PATH_MAX];
  int length=snprintf(temporary,sizeof temporary,"%s.tmp.XXXXXX",path);
  if(length<0||(size_t)length>=sizeof temporary)return 0;
  int fd=mkstemp(temporary);if(fd<0)return 0;
  if(close(fd)!=0){unlink(temporary);return 0;}
  int ok=RtlSaveSnapshot(temporary)&&Dkc1AndroidCommitFile(temporary,path);
  if(!ok)unlink(temporary);
  return ok;
}
static void SnapshotPath(char path[96],int slot) {
  if(slot==0)snprintf(path,96,"quicksave-%s.state",s_aspect_key);
  else snprintf(path,96,"quicksave-%s-slot%d.state",s_aspect_key,slot+1);
}
static void AutoSnapshot(void) {
  if(!atomic_load(&s_options[OPT_AUTOSAVE])||s_failed||!s_core_ready||!s_frame||s_last_auto_frame==s_frame)return;
  char path[96];snprintf(path,sizeof path,"autosave-%s.state",s_aspect_key);
  if(SaveSnapshotTo(path))s_last_auto_frame=s_frame;
  else Notice("No se pudo guardar el estado automatico; las ranuras manuales se conservan.");
}
static void ResetAudio(void) {
  Dkc1AudioStretchReset(&s_stretch);s_audio_average=-1;
  s_audio_accumulator=0;s_audio_started=0;
  if(s_audio){SDL_PauseAudioDevice(s_audio,1);SDL_ClearQueuedAudio(s_audio);}
  AudioTraceStats stats;audio_trace_get_stats(&stats);
  s_audio_threshold=538;
  s_audio_waiting=stats.occupancy_current<s_audio_threshold;
}
static void InitAudio(void) {
  if(s_audio){SDL_CloseAudioDevice(s_audio);s_audio=0;}
  SDL_AudioSpec want,got;SDL_zero(want);
  want.freq=AUDIO_RATE;want.format=AUDIO_S16SYS;want.channels=2;want.samples=1024;
  want.callback=NULL; /* SDL owns only the output queue, never the game/APU. */
  RtlSetAudioOutputRate(AUDIO_RATE);
  s_audio=SDL_OpenAudioDevice(NULL,0,&want,&got,0);
  if(!s_audio){Notice("Audio no disponible; el juego seguira sin sonido.");return;}
  s_audio_target=got.samples/2.0+2.0*534;
  ResetAudio();
  AudioTraceStats stats;audio_trace_get_stats(&stats);
  s_audio_threshold=stats.occupancy_current ? 538 : 2136;
  s_audio_waiting=stats.occupancy_current<s_audio_threshold;
}
static void PumpAudio(void) {
  if(s_audio_waiting){
    AudioTraceStats stats;audio_trace_get_stats(&stats);
    if(stats.occupancy_current<s_audio_threshold)return;
    s_audio_waiting=0;
  }
  s_audio_accumulator+=(double)AUDIO_RATE/60.0;
  int frames=(int)s_audio_accumulator;s_audio_accumulator-=frames;
  if(frames>AUDIO_CAPACITY)frames=AUDIO_CAPACITY;
  if(frames<=0)return;
  RtlRenderAudio(s_audio_in,frames,AUDIO_CHANNELS);
  Dkc1Msu1Mix(s_msu1,s_audio_in,frames,AUDIO_CHANNELS,AUDIO_RATE);
  ModMediaMix(s_audio_in,(size_t)frames,AUDIO_RATE);
  /* Even when muted or device-less, consume audio to keep the core bounded. */
  if(!s_audio||atomic_load(&s_options[OPT_SPEED]))return;
  unsigned queued=SDL_GetQueuedAudioSize(s_audio)/(2u*sizeof(int16_t));
  if(queued>AUDIO_RATE/4u){ResetAudio();return;}
  s_audio_average=Dkc1AudioFillAverage(s_audio_average,queued,0.02);
  double ratio=s_audio_started?Dkc1AudioRateRatio(s_audio_average,s_audio_target,0.005,4.0):1.0;
  frames=Dkc1AudioStretchProcess(&s_stretch,ratio,s_audio_in,frames,s_audio_out,AUDIO_CAPACITY+16);
  int volume=(atomic_load(&s_muted)||atomic_load(&s_options[OPT_MUTED]))?0:atomic_load(&s_options[OPT_VOLUME]);
  if(volume!=100)for(int i=0;i<frames*2;i++)s_audio_out[i]=(int16_t)((int)s_audio_out[i]*volume/100);
  if(SDL_QueueAudio(s_audio,s_audio_out,(Uint32)frames*2u*sizeof(int16_t))!=0){
    fprintf(stderr,"[audio] queue failed: %s\n",SDL_GetError());ResetAudio();return;
  }
  queued+=(unsigned)frames;
  if(!s_audio_started&&queued>=1068){SDL_PauseAudioDevice(s_audio,0);s_audio_started=1;}
}
static int CreateTexture(void) {
  if(Background())return 1; /* deferred until the surface can be used */
  if(s_texture){SDL_DestroyTexture(s_texture);s_texture=NULL;}
  if(s_sharp){SDL_DestroyTexture(s_sharp);s_sharp=NULL;s_sharp_scale=0;}
  s_width=Dkc1VideoWidth();
  if(s_width<kDkc1VideoNativeWidth||s_width>kDkc1VideoMaxWidth)return 0;
  s_sampling=-1;
  s_texture=SDL_CreateTexture(s_renderer,SDL_PIXELFORMAT_ARGB8888,
    SDL_TEXTUREACCESS_STREAMING,s_width,kDkc1VideoHeight);
  if(!s_texture)return 0;
  SDL_SetTextureBlendMode(s_texture,SDL_BLENDMODE_NONE);
  SDL_SetTextureScaleMode(s_texture,SDL_ScaleModeNearest);
  Dkc1BeginDrawing(s_pixels,(size_t)s_width*4);
  s_need_texture=0;return 1;
}
static int InitVideo(void) {
  SDL_SetHint(SDL_HINT_RENDER_DRIVER,"opengles2");
  SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY,"0");
  s_window=SDL_CreateWindow("DKC1 Android",SDL_WINDOWPOS_UNDEFINED,
    SDL_WINDOWPOS_UNDEFINED,960,540,SDL_WINDOW_FULLSCREEN_DESKTOP|SDL_WINDOW_ALLOW_HIGHDPI);
  if(!s_window)return 0;
  s_renderer=SDL_CreateRenderer(s_window,-1,SDL_RENDERER_ACCELERATED);
  if(!s_renderer)return 0;
  /* A single 60-Hz game clock. Do not turn a 90/120-Hz display into game speed. */
  if(SDL_RenderSetVSync(s_renderer,0)!=0)
    fprintf(stderr,"[video] disabling renderer vsync not supported: %s\n",SDL_GetError());
  SDL_SetRenderDrawColor(s_renderer,0,0,0,255);
  return CreateTexture();
}
static int Present(void) {
  if(Background())return 1;
  if(s_need_texture&&!CreateTexture())return 0;
  if(!s_texture)return 1;
  int width=0,height=0;
  if(SDL_GetRendererOutputSize(s_renderer,&width,&height)!=0||width<=0||height<=0)return 1;
  Dkc1AndroidRect fit=Dkc1AndroidFit(width,height,s_width,kDkc1VideoHeight);
  SDL_Rect destination={fit.x,fit.y,fit.w,fit.h};
  const int palette=atomic_load(&s_options[OPT_PALETTE]),sampling=atomic_load(&s_options[OPT_SAMPLING]);
  if(s_palette!=palette){
    if(!Dkc1DesktopColorFilterInit(&s_color_filter,palette)){
      Dkc1DesktopColorFilterInit(&s_color_filter,0);Notice("Modelo de color no disponible; se usa el original.");
    }
    s_palette=palette;
  }
  if(s_sampling!=sampling){SDL_SetTextureScaleMode(s_texture,sampling==1?SDL_ScaleModeLinear:SDL_ScaleModeNearest);s_sampling=sampling;}
  const uint8_t *display=Dkc1DesktopColorFilterApply(&s_color_filter,s_pixels,s_filtered,(size_t)s_width*kDkc1VideoHeight);
  if(!display)display=s_pixels;
  if(SDL_UpdateTexture(s_texture,NULL,display,s_width*4)!=0)return 0;
  SDL_SetRenderDrawColor(s_renderer,0,0,0,255);
  if(SDL_RenderClear(s_renderer)!=0)return 0;
  SDL_Texture *present_texture=s_texture;
  if(sampling==2&&SDL_RenderTargetSupported(s_renderer)){
    int scale=fit.w/s_width,vertical=fit.h/kDkc1VideoHeight;
    if(vertical<scale)scale=vertical;
    scale=ClampOption(scale,1,6);
    if(!s_sharp||s_sharp_scale!=scale){
      if(s_sharp)SDL_DestroyTexture(s_sharp);
      s_sharp=SDL_CreateTexture(s_renderer,SDL_PIXELFORMAT_ARGB8888,SDL_TEXTUREACCESS_TARGET,s_width*scale,kDkc1VideoHeight*scale);
      s_sharp_scale=scale;
      if(s_sharp){SDL_SetTextureBlendMode(s_sharp,SDL_BLENDMODE_NONE);SDL_SetTextureScaleMode(s_sharp,SDL_ScaleModeLinear);}
    }
    if(s_sharp&&SDL_SetRenderTarget(s_renderer,s_sharp)==0){
      SDL_RenderClear(s_renderer);
      int copied=SDL_RenderCopy(s_renderer,s_texture,NULL,NULL);
      if(SDL_SetRenderTarget(s_renderer,NULL)!=0)return 0;
      if(copied==0)present_texture=s_sharp;
    }
  }
  if(SDL_RenderCopy(s_renderer,present_texture,NULL,&destination)!=0)return 0;
  int lines=atomic_load(&s_options[OPT_SCANLINES]);
  if(lines>0&&fit.h>=kDkc1VideoHeight*2){
    SDL_SetRenderDrawBlendMode(s_renderer,SDL_BLENDMODE_BLEND);
    SDL_SetRenderDrawColor(s_renderer,0,0,0,(Uint8)(lines*255/100));
    for(int row=0;row<kDkc1VideoHeight;row++){
      int y=fit.y+(row*fit.h+fit.h*3/4)/kDkc1VideoHeight;
      SDL_RenderDrawLine(s_renderer,fit.x,y,fit.x+fit.w-1,y);
    }
    SDL_SetRenderDrawBlendMode(s_renderer,SDL_BLENDMODE_NONE);
  }
  SDL_RenderPresent(s_renderer);return 1;
}
static void OpenControllers(void) {
  for(int i=0;i<SDL_NumJoysticks();i++){
    if(!SDL_IsGameController(i))continue;
    SDL_JoystickID id=SDL_JoystickGetDeviceInstanceID(i);int known=0;
    for(int p=0;p<2;p++)if(s_pads[p]&&SDL_JoystickInstanceID(SDL_GameControllerGetJoystick(s_pads[p]))==id)known=1;
    if(known)continue;
    for(int p=0;p<2;p++)if(!s_pads[p]){s_pads[p]=SDL_GameControllerOpen(i);break;}
  }
}
static uint32_t ControllerInput(SDL_GameController *pad) {
  if(!pad||!SDL_GameControllerGetAttached(pad))return 0;
  uint32_t mask=0;
  /* SDL controller labels use Xbox positions; map to physical SNES positions. */
  const SDL_GameControllerButton buttons[12]={SDL_CONTROLLER_BUTTON_A,SDL_CONTROLLER_BUTTON_X,
    SDL_CONTROLLER_BUTTON_BACK,SDL_CONTROLLER_BUTTON_START,SDL_CONTROLLER_BUTTON_DPAD_UP,
    SDL_CONTROLLER_BUTTON_DPAD_DOWN,SDL_CONTROLLER_BUTTON_DPAD_LEFT,SDL_CONTROLLER_BUTTON_DPAD_RIGHT,
    SDL_CONTROLLER_BUTTON_B,SDL_CONTROLLER_BUTTON_Y,SDL_CONTROLLER_BUTTON_LEFTSHOULDER,SDL_CONTROLLER_BUTTON_RIGHTSHOULDER};
  for(int i=0;i<12;i++)if(SDL_GameControllerGetButton(pad,buttons[i]))mask|=1u<<i;
  if(atomic_load(&s_options[OPT_PAD_MAPPING])){
    unsigned face=mask&(1u|2u|256u|512u);mask&=~(1u|2u|256u|512u);
    mask|=((face&3u)<<8)|((face>>8)&3u);
  }
  int x=SDL_GameControllerGetAxis(pad,SDL_CONTROLLER_AXIS_LEFTX);
  int y=SDL_GameControllerGetAxis(pad,SDL_CONTROLLER_AXIS_LEFTY);
  int dead=atomic_load(&s_options[OPT_DEADZONE])*32767/100;
  if(x< -dead)mask|=64;if(x>dead)mask|=128;if(y< -dead)mask|=16;if(y>dead)mask|=32;
  return mask;
}
static uint32_t PollInput(void) {
  if(Paused())return 0;
  const Uint8 *keys=SDL_GetKeyboardState(NULL);
  const SDL_Scancode scancodes[12]={SDL_SCANCODE_Z,SDL_SCANCODE_X,SDL_SCANCODE_RSHIFT,
    SDL_SCANCODE_RETURN,SDL_SCANCODE_UP,SDL_SCANCODE_DOWN,SDL_SCANCODE_LEFT,SDL_SCANCODE_RIGHT,
    SDL_SCANCODE_S,SDL_SCANCODE_A,SDL_SCANCODE_Q,SDL_SCANCODE_W};
  uint32_t first=atomic_load(&s_touch)|ControllerInput(s_pads[0]);
  for(int i=0;i<12;i++)if(keys[scancodes[i]])first|=1u<<i;
  static int combo_held;
  int combo=0;
  for(int p=0;p<2;p++)if(s_pads[p]){
    combo|=SDL_GameControllerGetButton(s_pads[p],SDL_CONTROLLER_BUTTON_GUIDE)||
      (SDL_GameControllerGetButton(s_pads[p],SDL_CONTROLLER_BUTTON_START)&&
       SDL_GameControllerGetButton(s_pads[p],SDL_CONTROLLER_BUTTON_BACK));
  }
  if(combo&&!combo_held){combo_held=1;OpenMenu();return 0;}
  combo_held=combo;
  return Dkc1AndroidCleanInput(first)|(Dkc1AndroidCleanInput(ControllerInput(s_pads[1]))<<12);
}
static void PollEvents(void) {
  SDL_Event event;
  while(SDL_PollEvent(&event)){
    switch(event.type){
      case SDL_QUIT:s_running=0;break;
      case SDL_KEYDOWN:
        if(event.key.keysym.sym==SDLK_ESCAPE&&!event.key.repeat)OpenMenu();
        break;
      case SDL_APP_WILLENTERBACKGROUND:
      case SDL_WINDOWEVENT:
        if(event.type==SDL_APP_WILLENTERBACKGROUND||event.window.event==SDL_WINDOWEVENT_FOCUS_LOST)
          atomic_store(&s_touch,0);
        break;
      case SDL_RENDER_DEVICE_RESET:s_need_texture=1;break;
      case SDL_RENDER_TARGETS_RESET:s_need_texture=1;break;
      case SDL_CONTROLLERDEVICEADDED:OpenControllers();break;
      case SDL_CONTROLLERDEVICEREMOVED:
        for(int p=0;p<2;p++)if(s_pads[p]&&SDL_JoystickInstanceID(SDL_GameControllerGetJoystick(s_pads[p]))==event.cdevice.which){
          SDL_GameControllerClose(s_pads[p]);s_pads[p]=NULL;
        }
        break;
      case SDL_AUDIODEVICEREMOVED:
        if(!event.adevice.iscapture&&event.adevice.which==s_audio)InitAudio();
        break;
      case SDL_AUDIODEVICEADDED:
        if(!event.adevice.iscapture&&!s_audio)InitAudio();
        break;
      default:break;
    }
  }
}
static void ObserveMusic(void) {
  Dkc1Msu1ObserveMusicState(s_msu1,(uint16_t)(g_ram[0x0521]|(g_ram[0x0522]<<8)),
      (uint16_t)(g_ram[0x051d]|(g_ram[0x051e]<<8)));
}
static int CheckModRuntime(void) {
  if(interp_bridge_cartridge_faulted()){Failure("El romhack excedio los limites del nucleo. Sesion detenida sin guardar el estado fallido.");return 0;}
  if(!AddonRuntimeFaulted())return 1;
  Failure(AddonRuntimeError());return 0;
}
static void ClearRewind(void) {
  Dkc1RewindHistoryDestroy(&s_rewind);free(s_rewind_scratch);s_rewind_scratch=NULL;s_rewind_capacity=0;
}
static void CaptureRewind(void) {
  if(!atomic_load(&s_options[OPT_REWIND])||s_rewind_blocked||s_frame%6)return;
  size_t size=RtlSaveSnapshotToMemory(NULL,0);
  if(!size)return;
  if(size>s_rewind_capacity){
    ClearRewind();size_t capacity=512u*1024;
    while(capacity<size&&capacity<8u*1024*1024)capacity*=2;
    if(size>capacity){s_rewind_blocked=1;Notice("Rebobinado no disponible para este estado.");return;}
    size_t slot=capacity+sizeof(size_t),count=(32u*1024*1024)/slot;if(count>80)count=80;
    if(!Dkc1RewindHistoryInit(&s_rewind,slot,count)||!(s_rewind_scratch=malloc(slot))){ClearRewind();s_rewind_blocked=1;Notice("Memoria insuficiente para rebobinado.");return;}
    s_rewind_capacity=capacity;
  }
  // Zero padding is host-owned only, not cartridge state.
  memset(s_rewind_scratch,0,s_rewind_capacity+sizeof(size));memcpy(s_rewind_scratch,&size,sizeof size);
  if(RtlSaveSnapshotToMemory(s_rewind_scratch+sizeof size,s_rewind_capacity)==size)
    Dkc1RewindHistoryPush(&s_rewind,s_rewind_scratch);
}
static void Rewind(void) {
  if(!s_rewind.count||!s_rewind_scratch){Notice("Todavia no hay historial para retroceder.");return;}
  size_t count=s_rewind.count<21?s_rewind.count:21;
  for(size_t i=0;i<count;i++)if(!Dkc1RewindHistoryPop(&s_rewind,s_rewind_scratch))return;
  size_t size=0;memcpy(&size,s_rewind_scratch,sizeof size);
  if(size>s_rewind_capacity||!RtlLoadSnapshotFromMemory(s_rewind_scratch+sizeof size,size)){
    Failure("No se pudo recuperar el historial. La sesion se detuvo sin guardar.");return;
  }
  AddonRuntimeResetState();if(!CheckModRuntime())return;
  ResetAudio();Dkc1Msu1Reset(s_msu1);ObserveMusic();s_last_auto_frame=UINT64_MAX;
  atomic_store(&s_touch,0);Dkc1BeginDrawing(s_pixels,(size_t)Dkc1VideoWidth()*4);Dkc1DrawPpuFrame();s_ready_for_present=1;
  if(!CheckModRuntime())return;
  Notice("Partida retrocedida.");
}
static void HandleRequests(void) {
  if(!CheckModRuntime())return;
  if(atomic_exchange(&s_options_dirty,0)){
    s_ready_for_present=1;
    Dkc1VideoSetEdgePolicy((Dkc1EdgePolicy)atomic_load(&s_options[OPT_EDGE]));
    if(!atomic_load(&s_options[OPT_REWIND])){ClearRewind();s_rewind_blocked=0;}
  }
  unsigned user=atomic_exchange(&s_user_command,0);
  char slot_path[96];SnapshotPath(slot_path,ClampOption((int)((user>>8)&7),0,4));
  unsigned requests=atomic_exchange(&s_requests,0)|(user&19u);
  if(requests&CMD_QUIT){s_running=0;return;}
  if(requests&CMD_SRAM){if(!SaveSram())Notice("No se pudo guardar la SRAM.");AutoSnapshot();}
  if(requests&CMD_REWIND)Rewind();
  if(s_failed)return;
  if(requests&CMD_SAVE){
    int state_ok=SaveSnapshotTo(slot_path),sram_ok=SaveSram();
    Notice(state_ok&&sram_ok?"Estado rapido guardado.":"Error al guardar. El guardado anterior se conserva si fallo su escritura.");
  }
  if(requests&CMD_LOAD){
    if(access(slot_path,F_OK)!=0){Notice("No hay un estado rapido para este formato de pantalla.");return;}
    if(!RtlLoadSnapshot(slot_path)){
      /* A failed loader may have partially changed runtime state. Never run
       * or persist that state; restart the isolated game process instead. */
      Failure("No se pudo cargar el estado. Se ha detenido el juego sin guardar cambios.");return;
    }
    AddonRuntimeResetState();if(!CheckModRuntime())return;
    ResetAudio();ClearRewind();Dkc1Msu1Reset(s_msu1);ObserveMusic();s_last_auto_frame=UINT64_MAX;atomic_store(&s_touch,0);
    if(Dkc1VideoWidth()!=s_width)s_need_texture=1;
    Dkc1BeginDrawing(s_pixels,(size_t)Dkc1VideoWidth()*4);Dkc1DrawPpuFrame();
    if(!CheckModRuntime())return;
    s_ready_for_present=1;Notice("Estado rapido cargado.");
  }
}
static const char *Argument(int argc,char **argv,const char *name) {
  for(int i=1;i+1<argc;i++)if(!strcmp(argv[i],name))return argv[i+1];
  return NULL;
}
__attribute__((visibility("default"))) int SDL_main(int argc,char **argv) {
  const char *rom_path=Argument(argc,argv,"--rom"),*directory=Argument(argc,argv,"--state-dir");
  const char *aspect=Argument(argc,argv,"--aspect");
  uint8_t *rom=NULL;size_t rom_size=0;int result=0,compatibility=0,execution_mode=0;
  if(!rom_path||!directory||rom_path[0]!='/'||directory[0]!='/'){
    __android_log_write(ANDROID_LOG_ERROR,TAG,"Missing private ROM or state directory argument");return 2;
  }
  umask(077);
  if(mkdir(directory,0700)!=0&&errno!=EEXIST)return 2;
  if(chdir(directory)!=0)return 2;
  struct stat log_stat;
  if(stat("android.log",&log_stat)==0&&log_stat.st_size>1024*1024)
    (void)rename("android.log","android.log.1");
  (void)freopen("android.log","a",stderr);setvbuf(stderr,NULL,_IONBF,0);
  (void)unlink("last-error.txt");
  fprintf(stderr,"\n[start] android-1.6 base=cb4dae77 arm64 aspect=%s\n",aspect?aspect:"4:3");
  /* Keep speculative upstream widescreen switches off. Never weaken ROM verification. */
  (void)unsetenv("DKC1_ALLOW_ROM_SHA256");
  (void)setenv("DKC1_ENABLE_EXPERIMENTAL_CARTRIDGE_WIDENING","0",1);
  const char *aquatic=Argument(argc,argv,"--aquatic");
  const char *water=(aquatic&&strcmp(aquatic,"1")==0)?"1":"0";
  const char *flags[]={"DKC1_WS_PIXEL_BOUNDARIES","DKC1_WS_LIVE_SCROLL","DKC1_WS_SCROLL_REBASE","DKC1_WS_WALL_ADJACENCY","DKC1_WS_WALL_SEAMS"};
  for(int i=0;i<5;i++)(void)setenv(flags[i],water,1);
  (void)mkdir("tier2",0700);(void)setenv("SNESRECOMP_TIER2_DIR","tier2",1);
  SDL_SetHint(SDL_HINT_ORIENTATIONS,"LandscapeLeft LandscapeRight");
  SDL_SetHint(SDL_HINT_ANDROID_BLOCK_ON_PAUSE,"0");
  SDL_SetHint(SDL_HINT_TOUCH_MOUSE_EVENTS,"0");
  SDL_SetHint(SDL_HINT_MOUSE_TOUCH_EVENTS,"0");
  if(SDL_Init(SDL_INIT_VIDEO|SDL_INIT_AUDIO|SDL_INIT_GAMECONTROLLER|SDL_INIT_EVENTS)!=0){
    Failure(SDL_GetError());return 3;
  }
  SDL_AddEventWatch(EventWatch,NULL);BindJava();
  char error[256];rom=Dkc1ReadVerifiedRom(rom_path,&rom_size,error,sizeof error);
  if(!rom){Failure(error);result=2;goto cleanup;}
  const char *mod_key=Argument(argc,argv,"--mod-key"),*mod_plan=Argument(argc,argv,"--mod-plan");
  if(mod_key&&*mod_key){
    if(strlen(mod_key)!=16||strspn(mod_key,"0123456789abcdef")!=16||!mod_plan||!*mod_plan){Failure("Seleccion de mod invalida.");result=8;goto cleanup;}
    if(!DkcContentModApplyMode(rom,rom_size,mod_plan,Argument(argc,argv,"--mod-plan-sha"),&execution_mode,error,sizeof error)){Failure(error);result=8;goto cleanup;}
    snprintf(s_sram_name,sizeof s_sram_name,"dkc1-mod-%s.srm",mod_key);
    fprintf(stderr,"[mods] Applied verified package set %s execution=%s\n",mod_key,execution_mode==2?"aot-native-v1":execution_mode==1?"snes-compat-v1":"native/lua");
  }else if(mod_plan&&*mod_plan){Failure("El paquete no tiene identidad de sesion.");result=8;goto cleanup;}
  compatibility=execution_mode==1;
  const char *native_path=Argument(argc,argv,"--native-module");
  const int has_native=native_path&&*native_path;
  if(has_native!=(execution_mode==2)){Failure("El programa nativo no coincide con el plan del mod.");result=8;goto cleanup;}
  if(has_native&&!DkcNativeModuleLoad(native_path,Argument(argc,argv,"--native-module-sha"),rom,rom_size,error,sizeof error)){
    Failure(error);result=8;goto cleanup;
  }
  const char *runtime_path=Argument(argc,argv,"--runtime-file");
  const char *compat_arg=Argument(argc,argv,"--compatibility");
  const int requested_compat=compat_arg&&!strcmp(compat_arg,"1");
  if(requested_compat!=compatibility||(execution_mode&&runtime_path&&*runtime_path)){
    Failure("La modalidad del mod no coincide con el plan verificado.");result=8;goto cleanup;
  }
  interp_bridge_set_cartridge_compatibility(compatibility!=0);
  if(compatibility){
    aspect="4:3";
    for(int i=0;i<5;i++)(void)setenv(flags[i],"0",1);
  }
  if(runtime_path&&*runtime_path){
    if(!mod_key||!*mod_key){Failure("El mod no tiene identidad de sesion.");result=8;goto cleanup;}
    if(!AddonRuntimeLoad(runtime_path,Argument(argc,argv,"--runtime-sha"),Argument(argc,argv,"--runtime-options"),error,sizeof error)){
      Failure(error);result=8;goto cleanup;
    }
  }
  const char *music_directory=Argument(argc,argv,"--music-dir");
  if(execution_mode&&music_directory&&*music_directory){Failure("El romhack utiliza su propia musica; MSU-1 no se combina en esta modalidad.");result=8;goto cleanup;}
  if(music_directory&&*music_directory){
    for(unsigned track=1;track<=27;track++){
      char path[PATH_MAX];int n=snprintf(path,sizeof path,"%s/track-%u.pcm",music_directory,track);
      if(n<0||(size_t)n>=sizeof path||access(path,R_OK)!=0){
        Failure("Pack MSU-1 incompleto. Reimporta el pack o selecciona la musica original.");result=7;goto cleanup;
      }
    }
    s_msu1=Dkc1Msu1Open(music_directory,error,sizeof error);
    if(!s_msu1||!Dkc1Msu1ApplySpcMusicMute(rom,rom_size,error,sizeof error)){Failure(error);result=7;goto cleanup;}
  }
  int requested_width=kDkc1VideoNativeWidth;
  const char *render_width_arg=Argument(argc,argv,"--render-width");
  if(compatibility)requested_width=kDkc1VideoNativeWidth;
  else if(render_width_arg&&*render_width_arg){
    char *end=NULL;long parsed=strtol(render_width_arg,&end,10);
    if(!end||*end||parsed<kDkc1VideoNativeWidth||parsed>kDkc1VideoMaxWidth||(parsed&1)){
      Failure("Ancho de render invalido. Se requiere un numero par entre 256 y 448.");result=8;goto cleanup;
    }
    requested_width=(int)parsed;
  }else if(aspect&&!strcmp(aspect,"21:9"))requested_width=kDkc1VideoMaxWidth;
  else if(aspect&&!strcmp(aspect,"16:9"))requested_width=kDkc1VideoWidescreenWidth;
  else if(aspect&&!strcmp(aspect,"16:10"))requested_width=kDkc1VideoWidescreen16x10Width;
  if(!Dkc1VideoSetRenderWidth(requested_width)){
    Failure("El motor rechazo el ancho de render solicitado.");result=8;goto cleanup;
  }
  Dkc1VideoSetRom(rom,rom_size);RtlRegisterGame(Dkc1GameInfo());
  if(!SnesInit(rom,(int)rom_size)){Failure("El nucleo rechazo la ROM verificada.");result=4;goto cleanup;}
  s_core_ready=1;
  /* Seed a new mod SRAM from the original in memory, never overwrite it.
   * Snapshots are always isolated by the immutable active-package set. */
  if(!execution_mode&&strcmp(s_sram_name,"dkc1.srm")&&access(s_sram_name,F_OK)!=0&&access("dkc1.srm",F_OK)==0){
    if(g_sram&&g_sram_size>0&&!Dkc1AndroidReadExact("dkc1.srm",g_sram,(size_t)g_sram_size)){Failure("La SRAM original no es valida. Se conserva sin cambios.");result=6;goto cleanup;}
  }else LoadSram();
  if(s_failed){result=6;goto cleanup;}
  static char aspect_key_storage[24];
  if(aspect&&!strcmp(aspect,"device"))snprintf(aspect_key_storage,sizeof aspect_key_storage,"device%d",Dkc1VideoWidth());
  else if(aspect&&!strcmp(aspect,"21:9"))snprintf(aspect_key_storage,sizeof aspect_key_storage,"21x9");
  else if(Dkc1VideoGetAspect()==kDkc1VideoAspect16x9)snprintf(aspect_key_storage,sizeof aspect_key_storage,"16x9");
  else if(Dkc1VideoGetAspect()==kDkc1VideoAspect16x10)snprintf(aspect_key_storage,sizeof aspect_key_storage,"16x10");
  else snprintf(aspect_key_storage,sizeof aspect_key_storage,"4x3");
  s_aspect_key=aspect_key_storage;
  static char save_key[80];
  snprintf(save_key,sizeof save_key,"%s%s%s%s",s_aspect_key,s_msu1?"-msu1":"",mod_key&&*mod_key?"-mod-":"",mod_key?mod_key:"");s_aspect_key=save_key;
  const char *resume=Argument(argc,argv,"--resume");
  if(resume&&!strcmp(resume,"1")){
    char path[96];snprintf(path,sizeof path,"autosave-%s.state",s_aspect_key);
    if(access(path,F_OK)==0){
      if(!RtlLoadSnapshot(path)){Failure("El estado automatico no se pudo cargar. Vuelve al inicio y elige Iniciar desde el titulo.");result=6;goto cleanup;}
      AddonRuntimeResetState();if(!CheckModRuntime()){result=8;goto cleanup;}
      s_ready_for_present=1;
    }
  }
  s_width=Dkc1VideoWidth();Dkc1BeginDrawing(s_pixels,(size_t)s_width*4);
  if(!InitVideo()){Failure(SDL_GetError());result=3;goto cleanup;}
  ObserveMusic();InitAudio();OpenControllers();
  const double frequency=(double)SDL_GetPerformanceFrequency();
  Dkc1AndroidClock clock;Dkc1AndroidClockInit(&clock,(double)SDL_GetPerformanceCounter(),frequency);
  int was_paused=0;double stats_start=clock.next;
  uint64_t stats_frames=0;int previous_speed=-1;
  while(s_running){
    PollEvents();HandleRequests();if(!s_running)break;
    if(Paused()){
      if(!was_paused){ResetAudio();if(!SaveSram())Notice("No se pudo guardar la SRAM al pausar.");AutoSnapshot();}
      was_paused=1;
      if(!Background()&&s_ready_for_present){
        if(!Present()){Failure(SDL_GetError());result=3;break;}
        s_ready_for_present=0;
      }
      SDL_Delay(20);continue;
    }
    double now=(double)SDL_GetPerformanceCounter();
    if(was_paused){Dkc1AndroidClockInit(&clock,now,frequency);previous_speed=-1;ResetAudio();was_paused=0;
      stats_start=now;stats_frames=s_frame;}
    int speed=atomic_load(&s_options[OPT_SPEED]);
    if(speed!=previous_speed){clock.period=frequency/(speed?120.0:60.0);clock.next=now;ResetAudio();previous_speed=speed;}
    if(now<clock.next){
      double ms=(clock.next-now)*1000.0/frequency;
      SDL_Delay((Uint32)(ms>5?5:ms>=1?ms:1));continue;
    }
    if(s_need_texture&&!CreateTexture()){Failure(SDL_GetError());result=3;break;}
    uint32_t input=PollInput();if(Paused())continue;
    input=AddonRuntimeFilterInput(input,g_ram);
    if(!CheckModRuntime()){result=8;break;}
    Dkc1StompProbeCapture(&s_stomp_probe,g_ram);
    RtlRunFrame(input);
    if(Dkc1StompProbeAccepted(&s_stomp_probe,g_ram))StompHaptic();
    if(!CheckModRuntime()){result=8;break;}
    if(g_fail||!Dkc1LastLleResult()){
      snprintf(error,sizeof error,"El motor se detuvo en frame %llu, PC $%06x. Revisa android.log.",
        (unsigned long long)s_frame,(unsigned)Dkc1ResumePc());
      Failure(error);result=5;break;
    }
    Dkc1DrawPpuFrame();s_frame++;s_ready_for_present=1;
    AddonRuntimeEndFrame();
    if(!CheckModRuntime()){result=8;break;}
    ObserveMusic();PumpAudio();CaptureRewind();
    if(!Background()&&!Present()){Failure(SDL_GetError());result=3;break;}
    if(s_frame%300==0&&!SaveSram())Notice("No se pudo guardar la SRAM periodica.");
    if(s_frame%1800==0)AutoSnapshot();
    now=(double)SDL_GetPerformanceCounter();
    if(Dkc1AndroidClockAdvance(&clock,now))ResetAudio();
    if(s_frame%600==0){
      fprintf(stderr,"[run] frames=%llu fps=%.2f queued_bytes=%u reanchors=%llu\n",
        (unsigned long long)s_frame,(double)(s_frame-stats_frames)*frequency/(now-stats_start),
        s_audio?SDL_GetQueuedAudioSize(s_audio):0,(unsigned long long)clock.reanchors);
      stats_start=now;stats_frames=s_frame;
    }
  }
cleanup:
  /* Never overwrite the user's SRAM with a core state that went off-rails. */
  if(s_failed&&result==0)result=6;
  if(result==0){if(!SaveSram())Notice("No se pudo guardar la SRAM al salir.");AutoSnapshot();}
  if(s_audio){SDL_PauseAudioDevice(s_audio,1);SDL_CloseAudioDevice(s_audio);s_audio=0;}
  SDL_DelEventWatch(EventWatch,NULL);
  for(int p=0;p<2;p++)if(s_pads[p])SDL_GameControllerClose(s_pads[p]);
  ClearRewind();Dkc1Msu1Close(s_msu1);
  AddonRuntimeUnload();
  DkcNativeModuleUnload();
  if(s_sharp)SDL_DestroyTexture(s_sharp);
  if(s_texture)SDL_DestroyTexture(s_texture);
  if(s_renderer)SDL_DestroyRenderer(s_renderer);
  if(s_window)SDL_DestroyWindow(s_window);
  JNIEnv *env=JavaEnv();if(env&&s_activity)(*env)->DeleteGlobalRef(env,s_activity);
  free(s_last_sram);free(rom);
  fprintf(stderr,"[exit] result=%d frames=%llu\n",result,(unsigned long long)s_frame);
  SDL_Quit();return result;
}