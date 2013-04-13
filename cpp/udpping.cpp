
#include <sys/types.h>  
#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <arpa/inet.h>

#include <iostream>
#include <stdlib.h>

using namespace std;


/*
 * Just sends a simple UDP message to the specified hostname and port.
 * Used over java because even with all its advances native binaries still
 * have a faster startup time than small java programs.
 */
int main(int argc, char* argv[])
{
    if (argc != 3)
    {
        cout << "Syntax: " << argv[0] << " host port" << endl;
        return 1;
    }

    int s = socket(AF_INET, SOCK_DGRAM, 0);
    struct sockaddr_in si_other;

    memset((char *) &si_other, 0, sizeof(si_other));
    si_other.sin_family = AF_INET;
    si_other.sin_port = htons(atoi(argv[2]));
    inet_aton(argv[1], &si_other.sin_addr);
    sendto(s,"MSG",4,0,(sockaddr*)&si_other, sizeof(si_other));


}
