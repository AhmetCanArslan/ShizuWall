package com.arslan.shizuwall.shizuku;

import com.arslan.shizuwall.shizuku.IShizuWallForegroundListener;

interface IShizuWallUserService {
    void destroy() = 16777114;
    String setUidFirewallRule(int chain, int uid, int rule) = 1;
    String[] setUidFirewallRules(int chain, in int[] uids, in int[] rules) = 2;
    String getForegroundTask() = 3;
    void startForegroundWatch(IShizuWallForegroundListener listener) = 4;
    void stopForegroundWatch() = 5;
}
