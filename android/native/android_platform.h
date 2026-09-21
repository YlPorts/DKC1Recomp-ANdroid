/* Android host utilities. Project-authored additions: MIT, see ../LICENSE. */
#ifndef DKC1_ANDROID_PLATFORM_H
#define DKC1_ANDROID_PLATFORM_H
#include <stddef.h>
#include <stdint.h>

typedef struct Dkc1AndroidClock {
  double next, period;
  uint64_t reanchors;
} Dkc1AndroidClock;
typedef struct Dkc1AndroidRect { int x, y, w, h; } Dkc1AndroidRect;
void Dkc1AndroidClockInit(Dkc1AndroidClock *c, double now, double ticks_per_second);
int Dkc1AndroidClockAdvance(Dkc1AndroidClock *c, double now);
Dkc1AndroidRect Dkc1AndroidFit(int screen_w, int screen_h, int source_w, int source_h);
uint32_t Dkc1AndroidCleanInput(uint32_t mask);
/* All paths are app-private, and all these operations run on the game thread. */
int Dkc1AndroidAtomicWrite(const char *path, const void *data, size_t size);
int Dkc1AndroidCommitFile(const char *temporary, const char *destination);
int Dkc1AndroidReadExact(const char *path, void *out, size_t size);
/* Preserve an unreadable/invalid save under a unique sibling name. */
int Dkc1AndroidQuarantine(const char *path, char *backup, size_t capacity);
#endif
