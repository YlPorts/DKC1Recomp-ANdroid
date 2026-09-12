#define _POSIX_C_SOURCE 200809L
#include "android_platform.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

int main(void) {
  unsigned checks=0;
#define CHECK(condition) do { assert(condition);checks++; } while(0)
  Dkc1AndroidRect r=Dkc1AndroidFit(1280,720,256,224);
  CHECK(r.w==960&&r.h==720&&r.x==160&&r.y==0);
  r=Dkc1AndroidFit(1920,1080,256,224);
  CHECK(r.w==1440&&r.h==1080&&r.x==240);
  r=Dkc1AndroidFit(2400,1080,342,224);
  CHECK(r.w<=2400&&r.h==1080&&r.x>=0&&r.y==0);
  CHECK(fabs((double)r.w/r.h-342.0*7/(224*6))<0.002);
  r=Dkc1AndroidFit(0,720,256,224);CHECK(r.w==0&&r.h==0);
  r=Dkc1AndroidFit(1280,720,0,224);CHECK(r.w==0);
  r=Dkc1AndroidFit(1,1,256,224);CHECK(r.w==1&&r.h==1);
  for(int w=240;w<=3840;w+=73){
    for(int h=180;h<=2160;h+=119){
      r=Dkc1AndroidFit(w,h,342,224);
      CHECK(r.w>0&&r.h>0&&r.w<=w&&r.h<=h&&r.x>=0&&r.y>=0);
      CHECK(r.x*2+r.w<=w&&r.y*2+r.h<=h);
    }
  }
  for(unsigned input=0;input<65536;input++){
    unsigned clean=Dkc1AndroidCleanInput(input);
    CHECK(!(clean&~4095u));CHECK((clean&0x30u)!=0x30u);CHECK((clean&0xc0u)!=0xc0u);
    CHECK((clean&0xf0fu)==(input&0xf0fu&4095u));
  }
  Dkc1AndroidClock clock;
  Dkc1AndroidClockInit(&clock,0,1000000000.0);
  for(int i=0;i<600;i++)CHECK(!Dkc1AndroidClockAdvance(&clock,(double)i*clock.period));
  CHECK(fabs(clock.next-10e9)<0.01);
  CHECK(Dkc1AndroidClockAdvance(&clock,clock.next+100*clock.period));
  CHECK(clock.reanchors==1);
  Dkc1AndroidClockInit(&clock,5e12,1e9);
  CHECK(clock.next==5e12&&fabs(clock.period-1e9/60)<1e-5);
  /* Polling at different display rates never makes the game run at that rate. */
  const int polls[]={60,90,120,144};
  for(unsigned rate=0;rate<sizeof polls/sizeof polls[0];rate++){
    Dkc1AndroidClockInit(&clock,0,1e9);int frames=0;
    for(int i=0;i<polls[rate]*10;i++){
      double now=(double)i*1e9/polls[rate];
      if(now+0.1>=clock.next){frames++;Dkc1AndroidClockAdvance(&clock,now);}
    }
    CHECK(frames>=599&&frames<=600);
  }
  char directory[]="/tmp/dkc1-port-test-XXXXXX";CHECK(mkdtemp(directory)!=NULL);
  char path[512],staged[512],bad[512];
  snprintf(path,sizeof path,"%s/partida.srm",directory);
  snprintf(staged,sizeof staged,"%s/staged",directory);
  snprintf(bad,sizeof bad,"%s/no-such-directory/partida.srm",directory);
  unsigned char data[16],out[16],sentinel[16];
  for(int i=0;i<16;i++)data[i]=(unsigned char)(i*7);
  memset(sentinel,0xa5,sizeof sentinel);memcpy(out,sentinel,sizeof out);
  CHECK(Dkc1AndroidAtomicWrite(path,data,sizeof data));
  CHECK(Dkc1AndroidReadExact(path,out,sizeof out)&&!memcmp(data,out,sizeof out));
  memcpy(out,sentinel,sizeof out);
  CHECK(!Dkc1AndroidReadExact(path,out,15));CHECK(!memcmp(out,sentinel,sizeof out));
  CHECK(!Dkc1AndroidReadExact(bad,out,16));CHECK(!memcmp(out,sentinel,sizeof out));
  CHECK(!Dkc1AndroidAtomicWrite(bad,sentinel,16));
  CHECK(Dkc1AndroidReadExact(path,out,16)&&!memcmp(out,data,16));
  CHECK(Dkc1AndroidAtomicWrite(staged,sentinel,16));
  CHECK(!Dkc1AndroidCommitFile(staged,bad));
  CHECK(Dkc1AndroidReadExact(path,out,16)&&!memcmp(out,data,16));
  CHECK(Dkc1AndroidCommitFile(staged,path));
  CHECK(Dkc1AndroidReadExact(path,out,16)&&!memcmp(out,sentinel,16));
  CHECK(!Dkc1AndroidAtomicWrite(NULL,data,16));
  CHECK(!Dkc1AndroidAtomicWrite(path,NULL,16));
  CHECK(!Dkc1AndroidCommitFile(path,path));
  char backup[512],tiny[2];
  CHECK(!Dkc1AndroidQuarantine(path,tiny,sizeof tiny));
  CHECK(Dkc1AndroidReadExact(path,out,16)&&!memcmp(out,sentinel,16));
  CHECK(Dkc1AndroidQuarantine(path,backup,sizeof backup));
  CHECK(access(path,F_OK)!=0);
  CHECK(Dkc1AndroidReadExact(backup,out,16)&&!memcmp(out,sentinel,16));
  CHECK(!Dkc1AndroidQuarantine(path,staged,sizeof staged));
  CHECK(Dkc1AndroidReadExact(backup,out,16)&&!memcmp(out,sentinel,16));
  CHECK(unlink(backup)==0);CHECK(rmdir(directory)==0);
  printf("C platform tests: %u assertions passed\n",checks);
  return 0;
}
