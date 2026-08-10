package com.arslan.shizuwall.shizuku;

interface IShizuWallUserService {
    void destroy() = 16777114;
    String setUidFirewallRule(int chain, int uid, int rule) = 1;
    String[] setUidFirewallRules(int chain, in int[] uids, in int[] rules) = 2;
}
