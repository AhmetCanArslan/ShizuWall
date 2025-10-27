# ShizuWall

A lightweight Android firewall application that blocks network connections for selected apps **without requiring root access or VPN**. ShizuWall leverages Shizuku to provide powerful network control capabilities. Requires Android 11 (API 30) or higher.


### [Download from releases](https://github.com/ahmetcanarslan/shizuwall/releases) 

## Why ShizuWall is Different

1. **Shizuku-Only Approach**: Most Android firewalls require either Root access or VPN service, ShizuWall uses **only Shizuku**, providing native system-level control without drawbacks.

2. **True System Integration**: Uses Android's `connectivity` service directly via Shizuku to enable/disable networking per-app with the help of chain-3, rather than packet filtering.


3. **Zero Performance Impact**: No VPN tunnel means:
   - No battery drain from packet inspection
   - No connection speed reduction
   - No DNS leaks or routing issues


## Important Notes

- Firewall rules are automatically cleared on device reboot (Android security limitation)
- The app detects reboots using boot-relative timestamps and automatically clears stale state
- Only user-installed apps (non-system) are shown in the list
- If anything goes wrong, rebooting the phone will revert every change made by ShizuWall.


## Firewall Implementation

ShizuWall uses the following command structure via Shizuku:

```bash
# Enable firewall framework
cmd connectivity set-chain3-enabled true

# Block specific app
cmd connectivity set-package-networking-enabled false <package.name>

# Unblock specific app
cmd connectivity set-package-networking-enabled true <package.name>

# Disable firewall framework
cmd connectivity set-chain3-enabled false
```

## ⚠️ Disclaimer

This application requires Shizuku to function. The developer is not responsible for any issues arising from:
- Shizuku usage or configuration
- Network blocking affecting system functionality
- Data loss or service disruption
- Any negative consequences mentioned in the onboarding process

Use at your own discretion and ensure you understand which apps you're blocking.


## 🤝 Contributing

Contributions, issues, and feature requests are welcome!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request


- [Shizuku](https://github.com/RikkaApps/Shizuku) - For providing the API that makes this app possible
- Material Design 3 - For the beautiful UI components

---

**Note**: This is an unofficial application. It is not affiliated with Google, Android, or the Shizuku project.
