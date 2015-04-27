#import "WifiWorker.h"

@implementation WifiWorker

- (NSString *)getIP {
    NSString *address = @"error";
    struct ifaddrs *interfaces = NULL;
    struct ifaddrs *temp_addr = NULL;
    int success = 0;
    success = getifaddrs(&interfaces);
    if (success == 0) {
        temp_addr = interfaces;
        while(temp_addr != NULL) {
            if(temp_addr->ifa_addr->sa_family == AF_INET) {
                if([[NSString stringWithUTF8String:temp_addr->ifa_name] isEqualToString:@"en0"]) {
                    address = [NSString stringWithUTF8String:inet_ntoa(((struct sockaddr_in *)temp_addr->ifa_addr)->sin_addr)];
                }
            }
            temp_addr = temp_addr->ifa_next;
        }
    }
    freeifaddrs(interfaces);
    return address;
}

- (id)getSSIDinfo{
    NSArray *ifs = (__bridge_transfer id)CNCopySupportedInterfaces();
    id info = nil;
    for (NSString *ifnam in ifs) {
        info = (__bridge_transfer id)CNCopyCurrentNetworkInfo((__bridge CFStringRef)ifnam);
        if (info && [info count]) { break; }
    }
    return info;
}

- (void)getConnectedWifiInfo:(CDVInvokedUrlCommand*)command{
    CDVPluginResult* pluginResult = nil;
    NSString* ipaddr = [self getIP];
    NSMutableDictionary *dicInfo = [[NSMutableDictionary alloc] init];
    NSMutableDictionary* dicSSID = [self getSSIDinfo];
    [dicInfo setObject:[NSString stringWithFormat:@"%@",ipaddr] forKey:@"IPAddress"];
    [dicInfo setObject:[NSString stringWithFormat:@"%@",[dicSSID objectForKey:@"BSSID"]] forKey:@"BSSID"];
    [dicInfo setObject:[NSString stringWithFormat:@"%@",[dicSSID objectForKey:@"SSID"]] forKey:@"SSID"];
    [dicInfo setObject:@"" forKey:@"MacAddress"];
    if (ipaddr != nil && ![ipaddr isEqualToString:@"error"]) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:dicInfo];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR];
    }
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end

