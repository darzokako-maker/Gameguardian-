#include <jni.h>
#include <string>
#include <unistd.h>
#include <sys/syscall.h>
#include <fcntl.h>
#include <cstdlib>

// ARM64 mimarisi için kanca atılamaz doğrudan sistem çağrı numaraları
#if defined(__aarch64__)
    #define SYS_READ_KERNEL  __NR_pread64
    #define SYS_WRITE_KERNEL __NR_pwrite64
#else
    #define SYS_READ_KERNEL  180
    #define SYS_WRITE_KERNEL 181
#endif

// Java Katmanından tetiklenecek JNI Güvenli Hafıza Enjeksiyonu
extern "C" JNIEXPORT jboolean JNICALL
Java_com_nova_stealth_FloatingMenuService_writeMemorySyscall(JNIEnv* env, jobject thiz, jint pid, jlong address, jint value) {
    std::string mem_path = "/proc/" + std::to_string(pid) + "/mem";
    
    // Uygulama bu aşamada root haklarına sahip olmalıdır
    int mem_fd = open(mem_path.c_str(), O_WRONLY);
    if (mem_fd < 0) return JNI_FALSE;

    // İşlemci seviyesinde doğrudan enjeksiyon (Anti-cheat bypass)
    long bytes_written = syscall(SYS_WRITE_KERNEL, mem_fd, &value, sizeof(int), address);
    close(mem_fd);

    return (bytes_written > 0) ? JNI_TRUE : JNI_FALSE;
}

