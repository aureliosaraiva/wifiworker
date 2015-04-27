#import <Cordova/CDVPlugin.h>
#import <Cordova/CDVInvokedUrlCommand.h>
#import <Cordova/NSDictionary+Extensions.h>
#import <Cordova/NSArray+Comparisons.h>
#import <CoreBluetooth/CBService.h>
#import <Cordova/CDVJSON.h>
#import <SystemConfiguration/CaptiveNetwork.h>
#include <ifaddrs.h>
#include <arpa/inet.h>

@interface WifiWorker : CDVPlugin{
   
}
- (void)getConnectedWifiInfo:(CDVInvokedUrlCommand* )command;
@end
