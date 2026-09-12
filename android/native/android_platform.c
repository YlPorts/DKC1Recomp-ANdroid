#define _POSIX_C_SOURCE 200809L
#include "android_platform.h"
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>
#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

void Dkc1AndroidClockInit(Dkc1AndroidClock *c, double now, double frequency) {
  c->next = now;
  c->period = frequency > 0.0 ? frequency / 60.0 : 1.0;
  c->reanchors = 0;
}
int Dkc1AndroidClockAdvance(Dkc1AndroidClock *c, double now) {
  c->next += c->period;
  if (now - c->next > 3.0 * c->period) {
    c->next = now + c->period;
    c->reanchors++;
    return 1;
  }
  return 0;
}
Dkc1AndroidRect Dkc1AndroidFit(int sw, int sh, int w, int h) {
  Dkc1AndroidRect r = {0, 0, 0, 0};
  if (sw <= 0 || sh <= 0 || w <= 0 || h <= 0) return r;
  /* Match upstream's SNES 7:6 pixel aspect; never stretch to the phone. */
  const int64_t n = (int64_t)w * 7, d = (int64_t)h * 6;
  if ((int64_t)sw * d <= (int64_t)sh * n) {
    r.w = sw; r.h = (int)((int64_t)sw * d / n);
  } else {
    r.h = sh; r.w = (int)((int64_t)sh * n / d);
  }
  if (r.w < 1) r.w = 1;
  if (r.h < 1) r.h = 1;
  r.x = (sw - r.w) / 2; r.y = (sh - r.h) / 2;
  return r;
}
uint32_t Dkc1AndroidCleanInput(uint32_t mask) {
  mask &= UINT32_C(0xfff);
  if ((mask & 0x30u) == 0x30u) mask &= ~0x30u;
  if ((mask & 0xc0u) == 0xc0u) mask &= ~0xc0u;
  return mask;
}
static void SyncParent(const char *path) {
  char parent[PATH_MAX];
  if (snprintf(parent, sizeof parent, "%s", path) >= (int)sizeof parent) return;
  char *slash = strrchr(parent, '/');
  if (!slash) strcpy(parent, ".");
  else if (slash == parent) slash[1] = '\0';
  else *slash = '\0';
  int fd = open(parent, O_RDONLY);
  if (fd >= 0) { (void)fsync(fd); close(fd); }
}
int Dkc1AndroidCommitFile(const char *temporary, const char *destination) {
  if (!temporary || !destination || !strcmp(temporary, destination)) return 0;
  int fd = open(temporary, O_RDWR);
  if (fd < 0) return 0;
  int ok = fsync(fd) == 0;
  if (close(fd) != 0) ok = 0;
  if (!ok || rename(temporary, destination) != 0) return 0;
  SyncParent(destination);
  return 1;
}
int Dkc1AndroidAtomicWrite(const char *path, const void *data, size_t size) {
  if (!path || !*path || (!data && size)) return 0;
  char temporary[PATH_MAX];
  int n = snprintf(temporary, sizeof temporary, "%s.tmp.XXXXXX", path);
  if (n < 0 || (size_t)n >= sizeof temporary) return 0;
  int fd = mkstemp(temporary);
  if (fd < 0) return 0;
  const unsigned char *p = data;
  size_t done = 0;
  int ok = 1;
  while (done < size) {
    ssize_t count = write(fd, p + done, size - done);
    if (count < 0 && errno == EINTR) continue;
    if (count <= 0) { ok = 0; break; }
    done += (size_t)count;
  }
  if (ok && fsync(fd) != 0) ok = 0;
  if (close(fd) != 0) ok = 0;
  if (ok && rename(temporary, path) != 0) ok = 0;
  if (!ok) (void)unlink(temporary);
  else SyncParent(path);
  return ok;
}
int Dkc1AndroidReadExact(const char *path, void *out, size_t size) {
  if (!path || (!out && size)) return 0;
  FILE *f = fopen(path, "rb");
  if (!f) return 0;
  unsigned char *temporary = malloc(size ? size : 1);
  if (!temporary) { fclose(f); return 0; }
  int ok = fread(temporary, 1, size, f) == size && fgetc(f) == EOF && !ferror(f);
  if (fclose(f) != 0) ok = 0;
  if (ok && size) memcpy(out, temporary, size);
  free(temporary);
  return ok;
}

int Dkc1AndroidQuarantine(const char *path, char *backup, size_t capacity) {
  if (!path || !*path || !backup || !capacity) return 0;
  int n = snprintf(backup, capacity, "%s.invalid.XXXXXX", path);
  if (n < 0 || (size_t)n >= capacity) { backup[0] = '\0'; return 0; }
  int fd = mkstemp(backup);
  if (fd < 0) { backup[0] = '\0'; return 0; }
  int ok = close(fd) == 0;
  if (ok && rename(path, backup) != 0) ok = 0;
  if (!ok) { unlink(backup); backup[0] = '\0'; return 0; }
  SyncParent(backup);
  return 1;
}
