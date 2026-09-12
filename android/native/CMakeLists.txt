cmake_minimum_required(VERSION 3.22)
project(dkc1_android LANGUAGES C)
if(NOT ANDROID)
  message(FATAL_ERROR "Use the Android NDK toolchain (normally through Gradle).")
endif()
if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
  message(FATAL_ERROR "This initial port is scoped to arm64-v8a only.")
endif()
# Bound native compiler concurrency on modest development computers.
set_property(GLOBAL PROPERTY JOB_POOLS dkc1_compile=2 dkc1_link=1)
set(CMAKE_JOB_POOL_COMPILE dkc1_compile)
set(CMAKE_JOB_POOL_LINK dkc1_link)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)
get_filename_component(DKC1_ROOT "${CMAKE_CURRENT_LIST_DIR}/../.." ABSOLUTE)
set(SNESRECOMP_ROOT "${DKC1_ROOT}/snesrecomp")
set(SDL2_ROOT "${DKC1_ROOT}/android/third_party/SDL2")
set(GEN "${DKC1_ROOT}/generated/snesrecomp")
foreach(required "${SNESRECOMP_ROOT}/runner/runner.cmake" "${SDL2_ROOT}/CMakeLists.txt")
  if(NOT EXISTS "${required}")
    message(FATAL_ERROR "Missing ${required}. Run python android/tools/build_android.py --rom <ROM> --prepare-only")
  endif()
endforeach()
file(GLOB GEN_C CONFIGURE_DEPENDS "${GEN}/*.c")
if(NOT GEN_C OR NOT EXISTS "${GEN}/android-generation.json")
  message(FATAL_ERROR "Missing verified game sources. Run android/tools/build_android.py --rom <ROM> --prepare-only. No stub game is built.")
endif()
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
set(SDL2_DISABLE_INSTALL ON CACHE BOOL "" FORCE)
add_subdirectory("${SDL2_ROOT}" "${CMAKE_BINARY_DIR}/SDL2")
set(SNESRECOMP_SDL_BACKEND SDL2 CACHE STRING "" FORCE)
set(SNESRECOMP_ENABLE_TRACE OFF CACHE BOOL "" FORCE)
set(SNES_COSIM OFF CACHE BOOL "" FORCE)
set(SNESRECOMP_ENABLE_MODS OFF CACHE BOOL "" FORCE)
set(SNESRECOMP_ENABLE_FRAME_FINGERPRINTS OFF CACHE BOOL "" FORCE)
set(SNESRECOMP_ENABLE_PPU_DMA_HISTORY OFF CACHE BOOL "" FORCE)
set(SNESRECOMP_ENABLE_DISPATCH_HISTORY OFF CACHE BOOL "" FORCE)
include("${SNESRECOMP_ROOT}/runner/runner.cmake")
set(ENGINE ${SNESRECOMP_RUNNER_SOURCES})
list(REMOVE_ITEM ENGINE "${SNESRECOMP_ROOT}/runner/src/keybinds.c"
  "${SNESRECOMP_ROOT}/runner/src/host_report.c")
# Same game/runtime units as the upstream headless and desktop hosts.
# Deliberately do NOT compile sdl_host.c, windows_*.c or macos_*.m.
set(GAME
  dkc1_game.c dkc1_video.c dkc1_baby_kong.c dkc1_baby_kong_animation.c
  dkc1_baby_kong_movement.c dkc1_margin_proxy.c dkc1_ws_trace.c
  input_playback.c wram_dump.c dkc1_script.c dkc1_debug_dump.c
  dkc1_flight_recorder.c dkc1_blank_scan.c dkc1_invariant_monitor.c
  verified_rom.c headless_host.c desktop_audio_rate.c)
list(TRANSFORM GAME PREPEND "${DKC1_ROOT}/runner/")
add_library(main SHARED ${ENGINE} ${GEN_C} ${GAME}
  android_host.c android_platform.c)
target_include_directories(main PRIVATE "${DKC1_ROOT}/runner" "${DKC1_ROOT}/recomp"
  ${SNESRECOMP_RUNNER_INCLUDE_DIRS} "${CMAKE_CURRENT_LIST_DIR}")
target_compile_definitions(main PRIVATE SNESRECOMP_SDL3=0 SNESRECOMP_TRACE=0
  SNESRECOMP_REVERSE_DEBUG=0 SNESRECOMP_EXTERNAL_RAM_ROUTINE_GUARDS=1
  SYSTEM_VOLUME_MIXER_AVAILABLE=0 _POSIX_C_SOURCE=200809L
  DKC1_BUILD_COMMIT="cb4dae77" DKC1_BUILD_CONFIG="android-0.1.0-dev")
# The upstream runtime performs typed accesses to byte-backed guest memory.
target_compile_options(main PRIVATE -fno-strict-aliasing)
target_link_libraries(main PRIVATE SDL2::SDL2 ${SNESRECOMP_RUNNER_LIBRARIES} log android m)
if(TARGET SDL2main)
  target_link_libraries(main PRIVATE SDL2main)
endif()
# Explicit for both libraries, in addition to NDK r28's defaults.
foreach(t main SDL2)
  if(TARGET ${t})
    target_link_options(${t} PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
  endif()
endforeach()
# Link failures are errors, not silently deferred to the phone's loader.
target_link_options(main PRIVATE "-Wl,--no-undefined")
