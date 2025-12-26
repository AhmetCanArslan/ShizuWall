// adb_daemon.cpp
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <pthread.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/stat.h>

#define SOCKET_PATH "/data/local/tmp/adb_daemon.sock"

void* handle_client(void* arg) {
    int client_fd = *(int*)arg;
    char buffer[8192];
    
    int n = read(client_fd, buffer, sizeof(buffer)-1);
    if(n > 0) {
        buffer[n] = '\0';
        
        // Komutu çalıştır
        FILE* fp = popen(buffer, "r");
        if(fp) {
            while(fgets(buffer, sizeof(buffer), fp)) {
                write(client_fd, buffer, strlen(buffer));
            }
            pclose(fp);
        }
    }
    
    close(client_fd);
    free(arg);
    return NULL;
}

int main() {
    // Daemon olarak çalış
    if(fork() != 0) exit(0);
    setsid();
    if(fork() != 0) exit(0);
    
    // Socket oluştur
    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    unlink(SOCKET_PATH);
    
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, SOCKET_PATH, sizeof(addr.sun_path)-1);
    
    bind(server_fd, (struct sockaddr*)&addr, sizeof(addr));
    listen(server_fd, 10);
    chmod(SOCKET_PATH, 0666);
    
    // Sonsuz döngü
    while(1) {
        int* client_fd = (int*)malloc(sizeof(int));
        *client_fd = accept(server_fd, NULL, NULL);
        
        if(*client_fd > 0) {
            pthread_t thread;
            pthread_create(&thread, NULL, handle_client, client_fd);
            pthread_detach(thread);
        }
    }
    
    return 0;
}
