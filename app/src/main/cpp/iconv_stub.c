// Stub iconv implementation for Android NDK (API < 28)
// Android removed iconv from libc; this provides minimal stubs
// so that fontconfig/cairo can link. Actual charset conversion
// will fail at runtime if called, but map rendering doesn't use it.

#include <stddef.h>

typedef void* iconv_t;

iconv_t iconv_open(const char* to, const char* from) {
    return (iconv_t)-1; // return error
}

size_t iconv(iconv_t cd, char** inbuf, size_t* inbytesleft,
             char** outbuf, size_t* outbytesleft) {
    return (size_t)-1; // return error
}

int iconv_close(iconv_t cd) {
    return 0;
}
