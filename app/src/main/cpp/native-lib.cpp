#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <fstream>  
#include <fcntl.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <dirent.h>

#if defined(__aarch64__)
    #define SYS_READ_KERNEL  __NR_pread64
    #define SYS_WRITE_KERNEL __NR_pwrite64
#else
    #define SYS_READ_KERNEL  180
    #define SYS_WRITE_KERNEL 181
#endif

enum DataType { TYPE_DWORD = 1, TYPE_FLOAT = 2 };

struct MemoryRegion {
    long long start;
    long long end;
};

// Global arama sonuçları listesi (Aramalar arasında hafızada tutulur)
std::vector<long long> g_matches;

// Paket adına göre PID bulma
extern "C" JNIEXPORT jint JNICALL
Java_com_nova_stealth_FloatingMenuService_getPidByName(JNIEnv* env, jobject thiz, jstring j_package_name) {
    const char* package_name = env->GetStringUTFChars(j_package_name, nullptr);
    DIR* dir = opendir("/proc");
    if (!dir) {
        env->ReleaseStringUTFChars(j_package_name, package_name);
        return -1;
    }
    struct dirent* entry;
    int found_pid = -1;
    while ((entry = readdir(dir)) != nullptr) {
        int pid = atoi(entry->d_name);
        if (pid > 0) {
            std::string cmdline_path = "/proc/" + std::string(entry->d_name) + "/cmdline";
            int fd = open(cmdline_path.c_str(), O_RDONLY);
            if (fd >= 0) {
                char buf[256] = {0};
                read(fd, buf, sizeof(buf) - 1);
                close(fd);
                if (std::string(buf) == std::string(package_name)) {
                    found_pid = pid;
                    break;
                }
            }
        }
    }
    closedir(dir);
    env->ReleaseStringUTFChars(j_package_name, package_name);
    return found_pid;
}

std::vector<MemoryRegion> get_writable_regions(int pid) {
    std::vector<MemoryRegion> regions;
    std::string maps_path = "/proc/" + std::to_string(pid) + "/maps";
    std::ifstream file(maps_path);
    if (!file.is_open()) return regions;
    
    std::string line;
    // 🚨 KRİTİK DÜZELTME: std::get_line hatası std::getline olarak düzeltildi
    while (std::getline(file, line)) {
        if (line.find("rw-p") != std::string::npos && line.find("stack") == std::string::npos && line.find("ashmem") == std::string::npos) {
            char hyphen;
            long long start, end;
            std::stringstream ss(line);
            ss >> std::hex >> start >> hyphen >> std::hex >> end;
            regions.push_back({start, end});
        }
    }
    return regions;
}

// İlk Tarama (Syscall Destekli)
extern "C" JNIEXPORT jint JNICALL
Java_com_nova_stealth_FloatingMenuService_firstScan(JNIEnv* env, jobject thiz, jint pid, jint type, jfloat value) {
    g_matches.clear();
    std::vector<MemoryRegion> regions = get_writable_regions(pid);
    std::string mem_path = "/proc/" + std::to_string(pid) + "/mem";
    int mem_fd = open(mem_path.c_str(), O_RDONLY);
    if (mem_fd < 0) return 0;

    int target_dword = static_cast<int>(value);
    float target_float = value;

    for (const auto& region : regions) {
        size_t size = region.end - region.start;
        if (size <= 0 || size > 20 * 1024 * 1024) continue;

        std::vector<char> buffer(size);
        long bytes_read = syscall(SYS_READ_KERNEL, mem_fd, buffer.data(), size, region.start);
        if (bytes_read > 0) {
            for (size_t i = 0; i <= size - 4; i += 4) {
                if (type == TYPE_DWORD) {
                    int* cur = reinterpret_cast<int*>(&buffer[i]);
                    if (*cur == target_dword) g_matches.push_back(region.start + i);
                } else {
                    float* cur = reinterpret_cast<float*>(&buffer[i]);
                    if (*cur == target_float) g_matches.push_back(region.start + i);
                }
            }
        }
    }
    close(mem_fd);
    return g_matches.size();
}

// Ardışık Filtreleme Taraması (Next Scan)
extern "C" JNIEXPORT jint JNICALL
Java_com_nova_stealth_FloatingMenuService_nextScan(JNIEnv* env, jobject thiz, jint pid, jint type, jint mode, jfloat value) {
    if (g_matches.empty()) return 0;
    std::vector<long long> filtered;
    std::string mem_path = "/proc/" + std::to_string(pid) + "/mem";
    int mem_fd = open(mem_path.c_str(), O_RDONLY);
    if (mem_fd < 0) return 0;

    for (long long addr : g_matches) {
        if (type == TYPE_DWORD) {
            int current_val = 0;
            if (syscall(SYS_READ_KERNEL, mem_fd, &current_val, sizeof(int), addr) > 0) {
                if (mode == 1 && current_val == static_cast<int>(value)) filtered.push_back(addr);
            }
        } else {
            float current_val = 0.0f;
            if (syscall(SYS_READ_KERNEL, mem_fd, &current_val, sizeof(float), addr) > 0) {
                if (mode == 1 && current_val == value) filtered.push_back(addr);
            }
        }
    }
    close(mem_fd);
    g_matches = filtered;
    return g_matches.size();
}

// Hepsini Birden Değiştir (Toplu Yazma)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_nova_stealth_FloatingMenuService_writeAll(JNIEnv* env, jobject thiz, jint pid, jint type, jfloat value) {
    if (g_matches.empty()) return JNI_FALSE;
    std::string mem_path = "/proc/" + std::to_string(pid) + "/mem";
    int mem_fd = open(mem_path.c_str(), O_WRONLY);
    if (mem_fd < 0) return JNI_FALSE;

    int val_i = static_cast<int>(value);
    float val_f = value;

    for (long long addr : g_matches) {
        if (type == TYPE_DWORD) {
            syscall(SYS_WRITE_KERNEL, mem_fd, &val_i, sizeof(int), addr);
        } else {
            syscall(SYS_WRITE_KERNEL, mem_fd, &val_f, sizeof(float), addr);
        }
    }
    close(mem_fd);
    return JNI_TRUE;
}

// Tek Bir Adresi İndekse Göre Değiştirme
extern "C" JNIEXPORT jboolean JNICALL
Java_com_nova_stealth_FloatingMenuService_writeIndex(JNIEnv* env, jobject thiz, jint pid, jint index, jint type, jfloat value) {
    if (index < 0 || index >= g_matches.size()) return JNI_FALSE;
    std::string mem_path = "/proc/" + std::to_string(pid) + "/mem";
    int mem_fd = open(mem_path.c_str(), O_WRONLY);
    if (mem_fd < 0) return JNI_FALSE;

    long long addr = g_matches[index];
    long bytes = 0;
    if (type == TYPE_DWORD) {
        int val = static_cast<int>(value);
        bytes = syscall(SYS_WRITE_KERNEL, mem_fd, &val, sizeof(int), addr);
    } else {
        float val = value;
        bytes = syscall(SYS_WRITE_KERNEL, mem_fd, &val, sizeof(float), addr);
    }
    close(mem_fd);
    return (bytes > 0) ? JNI_TRUE : JNI_FALSE;
}

// Sonuç Listesini String Olarak Java'ya Döndürme
extern "C" JNIEXPORT jstring JNICALL
Java_com_nova_stealth_FloatingMenuService_getResultsString(JNIEnv* env, jobject thiz) {
    std::string result_str = "";
    for (size_t i = 0; i < g_matches.size() && i < 15; ++i) {
        std::stringstream ss;
        ss << "[" << i << "] Adres: 0x" << std::hex << g_matches[i] << "\n";
        result_str += ss.str();
    }
    if (g_matches.size() > 15) result_str += "...ve daha fazlası";
    if (g_matches.empty()) result_str = "Sonuç yok. Önce tarama yapın.";
    
    return env->NewStringUTF(result_str.c_str());
}

// Otomatik Ofset Analizi
extern "C" JNIEXPORT jstring JNICALL
Java_com_nova_stealth_FloatingMenuService_analyzePointer(JNIEnv* env, jobject thiz, jint pid, jint index) {
    if (index < 0 || index >= g_matches.size()) return env->NewStringUTF("Geçersiz İndeks!");
    long long target_addr = g_matches[index];
    std::vector<MemoryRegion> regions = get_writable_regions(pid);
    
    long long base_start = 0;
    for (const auto& r : regions) {
        if (target_addr >= r.start && target_addr <= r.end) {
            base_start = r.start;
            break;
        }
    }
    if (base_start == 0) return env->NewStringUTF("Hafıza bloğu tespit edilemedi.");
    
    long long offset = target_addr - base_start;
    std::stringstream ss;
    ss << "Base: 0x" << std::hex << base_start << "\nOffset: 0x" << std::hex << offset;
    
    return env->NewStringUTF(ss.str().c_str());
}
