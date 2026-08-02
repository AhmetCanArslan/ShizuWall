package com.arslan.shizuwall.shizuku;

interface IShizuWallUserService {
    void destroy() = 16777114;
    boolean setUidFirewallRule(int chain, int uid, int rule) = 1;
}
