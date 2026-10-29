// Vapor path redirect (LD_PRELOAD).
//
// Prebuilt parts of the Windows stack (gamescope's Xwayland/xkbcomp, the Vulkan wrapper, redirect shims,
// the Steam bootstrap) have "/data/data/app.gamenative/..." or "/data/user/0/app.gamenative/..." compiled
// in. Those paths belong to Android user 0, so inside Samsung Secure Folder, a work profile or a second
// user every such access fails with EACCES. Vapor preloads this library only in that case. It rewrites
// those two prefixes to the current user's data folder for the common file system calls.
//
// Environment (set by Vapor):
//   VAPOR_PATH_PKG  package name, e.g. "app.gamenative"
//   VAPOR_PATH_TO   real data dir, e.g. "/data/user/150/app.gamenative"
// Without both, every call passes straight through.
//
// Build: aarch64-linux-android26-clang -shared -fPIC -O2 -o libvaporpath.so vaporpath.c -ldl

#define _GNU_SOURCE
#include <dlfcn.h>
#include <dirent.h>
#include <fcntl.h>
#include <limits.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/un.h>
#include <unistd.h>

#define VP_MAX 512

static char g_from1[VP_MAX]; /* /data/data/<pkg>   */
static char g_from2[VP_MAX]; /* /data/user/0/<pkg> */
static char g_to[VP_MAX];
static size_t g_len1, g_len2, g_lento;
static int g_active;

__attribute__((constructor)) static void vp_init(void)
{
    const char *pkg = getenv("VAPOR_PATH_PKG");
    const char *to = getenv("VAPOR_PATH_TO");
    if (!pkg || !*pkg || !to || !*to) return;
    snprintf(g_from1, sizeof(g_from1), "/data/data/%s", pkg);
    snprintf(g_from2, sizeof(g_from2), "/data/user/0/%s", pkg);
    snprintf(g_to, sizeof(g_to), "%s", to);
    g_len1 = strlen(g_from1);
    g_len2 = strlen(g_from2);
    g_lento = strlen(g_to);
    while (g_lento > 1 && g_to[g_lento - 1] == '/') g_to[--g_lento] = 0;
    /* Nothing to do when the real folder already is one of the user-0 spellings. */
    if (!strcmp(g_to, g_from1) || !strcmp(g_to, g_from2)) return;
    g_active = 1;
}

/* Returns `path`, or `buf` filled with the rewritten path. */
static const char *vp_fix(const char *path, char *buf, size_t size)
{
    if (!g_active || !path || path[0] != '/') return path;
    size_t n = 0;
    if (!strncmp(path, g_from1, g_len1) && (path[g_len1] == '/' || path[g_len1] == 0)) n = g_len1;
    else if (!strncmp(path, g_from2, g_len2) && (path[g_len2] == '/' || path[g_len2] == 0)) n = g_len2;
    else return path;
    size_t rest = strlen(path + n);
    if (g_lento + rest + 1 > size) return path;
    memcpy(buf, g_to, g_lento);
    memcpy(buf + g_lento, path + n, rest + 1);
    return buf;
}

#define REAL(ret, name, ...) \
    static ret (*real_##name)(__VA_ARGS__); \
    if (!real_##name) real_##name = (ret (*)(__VA_ARGS__)) dlsym(RTLD_NEXT, #name)

#define FIXED(p) char p##_buf[PATH_MAX]; const char *p##_f = vp_fix(p, p##_buf, sizeof(p##_buf))

static int has_mode(int flags)
{
#ifdef O_TMPFILE
    if ((flags & O_TMPFILE) == O_TMPFILE) return 1;
#endif
    return (flags & O_CREAT) != 0;
}

int open(const char *path, int flags, ...)
{
    REAL(int, open, const char *, int, ...);
    mode_t mode = 0;
    if (has_mode(flags)) { va_list ap; va_start(ap, flags); mode = (mode_t) va_arg(ap, int); va_end(ap); }
    FIXED(path);
    return real_open(path_f, flags, mode);
}

int open64(const char *path, int flags, ...)
{
    REAL(int, open64, const char *, int, ...);
    mode_t mode = 0;
    if (has_mode(flags)) { va_list ap; va_start(ap, flags); mode = (mode_t) va_arg(ap, int); va_end(ap); }
    FIXED(path);
    if (!real_open64) { REAL(int, open, const char *, int, ...); return real_open(path_f, flags, mode); }
    return real_open64(path_f, flags, mode);
}

int openat(int dirfd, const char *path, int flags, ...)
{
    REAL(int, openat, int, const char *, int, ...);
    mode_t mode = 0;
    if (has_mode(flags)) { va_list ap; va_start(ap, flags); mode = (mode_t) va_arg(ap, int); va_end(ap); }
    FIXED(path);
    return real_openat(dirfd, path_f, flags, mode);
}

int __open_2(const char *path, int flags)
{
    REAL(int, __open_2, const char *, int);
    FIXED(path);
    return real___open_2(path_f, flags);
}

int __openat_2(int dirfd, const char *path, int flags)
{
    REAL(int, __openat_2, int, const char *, int);
    FIXED(path);
    return real___openat_2(dirfd, path_f, flags);
}

int creat(const char *path, mode_t mode)
{
    REAL(int, creat, const char *, mode_t);
    FIXED(path);
    return real_creat(path_f, mode);
}

FILE *fopen(const char *path, const char *mode)
{
    REAL(FILE *, fopen, const char *, const char *);
    FIXED(path);
    return real_fopen(path_f, mode);
}

FILE *fopen64(const char *path, const char *mode)
{
    REAL(FILE *, fopen64, const char *, const char *);
    FIXED(path);
    if (!real_fopen64) { REAL(FILE *, fopen, const char *, const char *); return real_fopen(path_f, mode); }
    return real_fopen64(path_f, mode);
}

FILE *freopen(const char *path, const char *mode, FILE *stream)
{
    REAL(FILE *, freopen, const char *, const char *, FILE *);
    FIXED(path);
    return real_freopen(path_f, mode, stream);
}

int access(const char *path, int mode)
{
    REAL(int, access, const char *, int);
    FIXED(path);
    return real_access(path_f, mode);
}

int faccessat(int dirfd, const char *path, int mode, int flags)
{
    REAL(int, faccessat, int, const char *, int, int);
    FIXED(path);
    return real_faccessat(dirfd, path_f, mode, flags);
}

int stat(const char *path, struct stat *st)
{
    REAL(int, stat, const char *, struct stat *);
    FIXED(path);
    return real_stat(path_f, st);
}

int lstat(const char *path, struct stat *st)
{
    REAL(int, lstat, const char *, struct stat *);
    FIXED(path);
    return real_lstat(path_f, st);
}

int fstatat(int dirfd, const char *path, struct stat *st, int flags)
{
    REAL(int, fstatat, int, const char *, struct stat *, int);
    FIXED(path);
    return real_fstatat(dirfd, path_f, st, flags);
}

struct statx;
int statx(int dirfd, const char *path, int flags, unsigned int mask, struct statx *buf)
{
    REAL(int, statx, int, const char *, int, unsigned int, struct statx *);
    FIXED(path);
    if (!real_statx) return -1;
    return real_statx(dirfd, path_f, flags, mask, buf);
}

int mkdir(const char *path, mode_t mode)
{
    REAL(int, mkdir, const char *, mode_t);
    FIXED(path);
    return real_mkdir(path_f, mode);
}

int mkdirat(int dirfd, const char *path, mode_t mode)
{
    REAL(int, mkdirat, int, const char *, mode_t);
    FIXED(path);
    return real_mkdirat(dirfd, path_f, mode);
}

int rmdir(const char *path)
{
    REAL(int, rmdir, const char *);
    FIXED(path);
    return real_rmdir(path_f);
}

int unlink(const char *path)
{
    REAL(int, unlink, const char *);
    FIXED(path);
    return real_unlink(path_f);
}

int unlinkat(int dirfd, const char *path, int flags)
{
    REAL(int, unlinkat, int, const char *, int);
    FIXED(path);
    return real_unlinkat(dirfd, path_f, flags);
}

int rename(const char *oldp, const char *newp)
{
    REAL(int, rename, const char *, const char *);
    FIXED(oldp);
    FIXED(newp);
    return real_rename(oldp_f, newp_f);
}

int renameat(int od, const char *oldp, int nd, const char *newp)
{
    REAL(int, renameat, int, const char *, int, const char *);
    FIXED(oldp);
    FIXED(newp);
    return real_renameat(od, oldp_f, nd, newp_f);
}

int link(const char *oldp, const char *newp)
{
    REAL(int, link, const char *, const char *);
    FIXED(oldp);
    FIXED(newp);
    return real_link(oldp_f, newp_f);
}

int symlink(const char *target, const char *linkp)
{
    REAL(int, symlink, const char *, const char *);
    FIXED(target);
    FIXED(linkp);
    return real_symlink(target_f, linkp_f);
}

ssize_t readlink(const char *path, char *buf, size_t size)
{
    REAL(ssize_t, readlink, const char *, char *, size_t);
    FIXED(path);
    return real_readlink(path_f, buf, size);
}

char *realpath(const char *path, char *resolved)
{
    REAL(char *, realpath, const char *, char *);
    FIXED(path);
    return real_realpath(path_f, resolved);
}

DIR *opendir(const char *path)
{
    REAL(DIR *, opendir, const char *);
    FIXED(path);
    return real_opendir(path_f);
}

int chdir(const char *path)
{
    REAL(int, chdir, const char *);
    FIXED(path);
    return real_chdir(path_f);
}

int chmod(const char *path, mode_t mode)
{
    REAL(int, chmod, const char *, mode_t);
    FIXED(path);
    return real_chmod(path_f, mode);
}

int fchmodat(int dirfd, const char *path, mode_t mode, int flags)
{
    REAL(int, fchmodat, int, const char *, mode_t, int);
    FIXED(path);
    return real_fchmodat(dirfd, path_f, mode, flags);
}

int truncate(const char *path, off_t len)
{
    REAL(int, truncate, const char *, off_t);
    FIXED(path);
    return real_truncate(path_f, len);
}

int utimensat(int dirfd, const char *path, const struct timespec times[2], int flags)
{
    REAL(int, utimensat, int, const char *, const struct timespec *, int);
    FIXED(path);
    return real_utimensat(dirfd, path_f, times, flags);
}

int execve(const char *path, char *const argv[], char *const envp[])
{
    REAL(int, execve, const char *, char *const *, char *const *);
    FIXED(path);
    return real_execve(path_f, argv, envp);
}

int execv(const char *path, char *const argv[])
{
    REAL(int, execv, const char *, char *const *);
    FIXED(path);
    return real_execv(path_f, argv);
}

void *dlopen(const char *path, int flags)
{
    REAL(void *, dlopen, const char *, int);
    FIXED(path);
    return real_dlopen(path_f, flags);
}

/* Unix sockets with a user-0 path (e.g. under gamescope/tmp). */
static const struct sockaddr *vp_fix_sockaddr(const struct sockaddr *addr, socklen_t *len, struct sockaddr_un *tmp)
{
    if (!g_active || !addr || addr->sa_family != AF_UNIX || *len <= (socklen_t) sizeof(sa_family_t)) return addr;
    const struct sockaddr_un *un = (const struct sockaddr_un *) addr;
    if (un->sun_path[0] != '/') return addr; /* abstract or relative */
    char path[sizeof(un->sun_path) + 1];
    size_t n = (size_t) *len - offsetof(struct sockaddr_un, sun_path);
    if (n > sizeof(un->sun_path)) n = sizeof(un->sun_path);
    memcpy(path, un->sun_path, n);
    path[n] = 0;
    char buf[PATH_MAX];
    const char *f = vp_fix(path, buf, sizeof(buf));
    if (f == path || strlen(f) >= sizeof(tmp->sun_path)) return addr;
    memset(tmp, 0, sizeof(*tmp));
    tmp->sun_family = AF_UNIX;
    strcpy(tmp->sun_path, f);
    *len = (socklen_t) (offsetof(struct sockaddr_un, sun_path) + strlen(f) + 1);
    return (const struct sockaddr *) tmp;
}

int connect(int fd, const struct sockaddr *addr, socklen_t len)
{
    REAL(int, connect, int, const struct sockaddr *, socklen_t);
    struct sockaddr_un tmp;
    const struct sockaddr *a = vp_fix_sockaddr(addr, &len, &tmp);
    return real_connect(fd, a, len);
}

int bind(int fd, const struct sockaddr *addr, socklen_t len)
{
    REAL(int, bind, int, const struct sockaddr *, socklen_t);
    struct sockaddr_un tmp;
    const struct sockaddr *a = vp_fix_sockaddr(addr, &len, &tmp);
    return real_bind(fd, a, len);
}
