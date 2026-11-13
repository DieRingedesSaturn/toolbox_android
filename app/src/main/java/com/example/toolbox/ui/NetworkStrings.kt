package com.example.toolbox.ui

import com.example.toolbox.network.NetworkTransport

val ToolboxStrings.networkDescription: String
    get() = if (chinese) "查看当前网络的连接状态、本地 IP 地址、DNS 服务器与公网出口信息。" else "View network connection status, local IP addresses, DNS servers, and public egress info."

val ToolboxStrings.networkConnectionTitle: String
    get() = if (chinese) "连接详情" else "Connection details"

val ToolboxStrings.networkConnection: String
    get() = if (chinese) "连接状态" else "Connection"

val ToolboxStrings.networkTransport: String
    get() = if (chinese) "传输方式" else "Transport"

val ToolboxStrings.networkInterface: String
    get() = if (chinese) "网络接口" else "Interface"

val ToolboxStrings.networkMetered: String
    get() = if (chinese) "按流量计费" else "Metered"

val ToolboxStrings.localAddresses: String
    get() = if (chinese) "本地地址" else "Local addresses"

val ToolboxStrings.dnsServers: String
    get() = if (chinese) "DNS 服务器" else "DNS servers"

val ToolboxStrings.gateways: String
    get() = if (chinese) "网关" else "Gateways"

val ToolboxStrings.networkPermissionTitle: String
    get() = if (chinese) "网络权限" else "Network permissions"

val ToolboxStrings.networkPermissionDescription: String
    get() = if (chinese) {
        "用于读取本地网络连接状态与路由接口信息；局域网扫描时会加入组播以接收 mDNS/SSDP/ONVIF 服务通告，且仅在你点击「开始扫描」时运行。"
    } else {
        "Used to read local connection status and routing details; a LAN scan joins multicast to receive mDNS/SSDP/ONVIF announcements and only runs after you tap Scan."
    }

val ToolboxStrings.publicIpTitle: String
    get() = if (chinese) "公网 IP" else "Public IP"

val ToolboxStrings.publicIp: String
    get() = if (chinese) "公网地址" else "Public address"

val ToolboxStrings.publicIpLocation: String
    get() = if (chinese) "大致位置" else "Approximate location"

val ToolboxStrings.publicIpIsp: String
    get() = if (chinese) "运营商 / 提供商" else "ISP / Provider"

val ToolboxStrings.publicIpVersion: String
    get() = if (chinese) "IP 类型" else "IP type"

val ToolboxStrings.publicIpCountryCode: String
    get() = if (chinese) "国家/地区代码" else "Country code"

val ToolboxStrings.publicIpContinent: String
    get() = if (chinese) "大洲" else "Continent"

val ToolboxStrings.publicIpPostal: String
    get() = if (chinese) "邮编" else "Postal code"

val ToolboxStrings.publicIpTimezone: String
    get() = if (chinese) "时区" else "Time zone"

val ToolboxStrings.publicIpAsn: String
    get() = "ASN"

val ToolboxStrings.publicIpOrganization: String
    get() = if (chinese) "组织" else "Organization"

val ToolboxStrings.publicIpDomain: String
    get() = if (chinese) "网络域名" else "Network domain"

val ToolboxStrings.publicIpHostname: String
    get() = if (chinese) "主机名" else "Hostname"

val ToolboxStrings.publicIpRange: String
    get() = if (chinese) "IP 段" else "IP range"

val ToolboxStrings.publicIpNetworkType: String
    get() = if (chinese) "网络类型" else "Network type"

val ToolboxStrings.publicIpProxy: String
    get() = if (chinese) "代理/VPN 标记" else "Proxy/VPN flag"

val ToolboxStrings.publicIpRisk: String
    get() = if (chinese) "风险评分" else "Risk score"

val ToolboxStrings.publicIpRiskLow: String
    get() = if (chinese) "低" else "Low"

val ToolboxStrings.publicIpRiskMedium: String
    get() = if (chinese) "中" else "Medium"

val ToolboxStrings.publicIpRiskHigh: String
    get() = if (chinese) "高" else "High"

val ToolboxStrings.publicIpSource: String
    get() = if (chinese) "数据来源" else "Data source"

val ToolboxStrings.publicIpOpenIplark: String
    get() = if (chinese) "在 IPLark 查看完整报告" else "View full report on IPLark"

val ToolboxStrings.publicIpDescription: String
    get() = if (chinese) {
        "仅点击查询时访问公开数据源，只发送所查 IP。代理/风险针对出口 IP，仅供参考。"
    } else {
        "Sources are contacted only on Query, sending just that IP. Proxy/risk hints describe the exit IP."
    }

fun ToolboxStrings.publicIpNetworkType(value: String): String = when (value.trim().lowercase()) {
    "business" -> if (chinese) "商业网络" else "Business"
    "residential" -> if (chinese) "住宅网络" else "Residential"
    "wireless" -> if (chinese) "无线网络" else "Wireless"
    "hosting" -> if (chinese) "数据中心 / 托管" else "Hosting"
    "vpn" -> "VPN"
    "tor" -> "Tor"
    "public proxy" -> if (chinese) "公共代理" else "Public proxy"
    "compromised server" -> if (chinese) "受控服务器" else "Compromised server"
    "scraper" -> if (chinese) "爬虫" else "Scraper"
    else -> value
}

val ToolboxStrings.queryPublicIp: String
    get() = if (chinese) "查询公网 IP" else "Query public IP"

val ToolboxStrings.publicIpNotRequested: String
    get() = if (chinese) "尚未查询" else "Not requested"

val ToolboxStrings.lanScanTitle: String
    get() = if (chinese) "局域网设备" else "LAN devices"

val ToolboxStrings.lanScanAction: String
    get() = if (chinese) "开始扫描" else "Start scan"

val ToolboxStrings.lanScanStop: String
    get() = if (chinese) "停止" else "Stop"

val ToolboxStrings.lanScanDiscovering: String
    get() = if (chinese) "正在发现设备…" else "Discovering devices…"

fun ToolboxStrings.lanScanPortProgress(done: Int, total: Int): String =
    if (chinese) "扫描端口中 $done/$total…" else "Scanning ports $done/$total…"

val ToolboxStrings.lanScanListening: String
    get() = if (chinese) "正在收集服务通告…" else "Collecting service announcements…"

val ToolboxStrings.lanScanNoNetwork: String
    get() = if (chinese) "未连接到局域网（需要 Wi-Fi 或以太网）" else
        "Not on a local network (Wi-Fi or Ethernet required)"

val ToolboxStrings.lanScanFailed: String
    get() = if (chinese) "扫描失败，请重试" else "Scan failed; please try again"

fun ToolboxStrings.lanScanFound(count: Int, subnet: String): String =
    if (chinese) "网段 $subnet：发现 $count 台设备" else "Subnet $subnet: $count devices found"

val ToolboxStrings.lanScanNotRun: String
    get() = if (chinese) "尚未扫描" else "Not scanned yet"

val ToolboxStrings.lanScanHint: String
    get() = if (chinese) {
        "设备发现 + 摄像头启发式检测：仅列出与手机同一局域网的设备。开放 RTSP/ONVIF 等摄像头" +
            "典型服务或摄像头厂商私有端口、网卡为摄像头厂商的会标注。自带热点、仅连云或未" +
            "开放端口的摄像头无法被发现；普通设备开放管理端口也可能被标注，仅供参考。"
    } else {
        "Device discovery plus camera heuristics: only devices on the same LAN are listed. " +
            "Devices exposing camera-typical services (RTSP/ONVIF), camera vendor ports, or " +
            "camera-vendor NICs are flagged. Cameras on their own hotspot, cloud-only cameras, " +
            "or those with no open ports cannot be seen; ordinary devices with admin ports may " +
            "be flagged too — treat results as hints."
    }

val ToolboxStrings.lanDeviceSelf: String
    get() = if (chinese) "本机" else "This device"

val ToolboxStrings.lanDeviceGateway: String
    get() = if (chinese) "网关" else "Gateway"

val ToolboxStrings.lanDeviceMac: String
    get() = if (chinese) "MAC" else "MAC"

val ToolboxStrings.lanDeviceHostname: String
    get() = if (chinese) "主机名" else "Hostname"

val ToolboxStrings.lanDeviceVendor: String
    get() = if (chinese) "厂商推测" else "Vendor guess"

val ToolboxStrings.lanDeviceServices: String
    get() = if (chinese) "广播的服务" else "Advertised services"

val ToolboxStrings.lanDeviceOpenPorts: String
    get() = if (chinese) "开放端口" else "Open ports"

val ToolboxStrings.lanSuspicionLikely: String
    get() = if (chinese) "疑似摄像头/监控设备" else "Likely camera/NVR"

val ToolboxStrings.lanSuspicionNoteworthy: String
    get() = if (chinese) "摄像头厂商网卡" else "Camera-vendor NIC"

fun ToolboxStrings.lanReasonRtsp(ports: String): String =
    if (chinese) "开放 RTSP 端口 $ports" else "RTSP port(s) $ports open"

fun ToolboxStrings.lanReasonCameraPort(detail: String): String =
    if (chinese) "开放摄像头常见端口：$detail" else "Camera-typical port(s) open: $detail"

val ToolboxStrings.lanReasonOnvif: String
    get() = if (chinese) "广播了 ONVIF/摄像头服务" else "Advertises an ONVIF/camera service"

fun ToolboxStrings.lanReasonVendor(vendor: String): String =
    if (chinese) "网卡厂商：$vendor" else "NIC vendor: $vendor"

val ToolboxStrings.lanReasonWebAdmin: String
    get() = if (chinese) "仅开放管理端口（80/443/8080 等），注意核对" else
        "Only admin port(s) open (80/443/8080…); worth a look"

val ToolboxStrings.connected: String
    get() = if (chinese) "已连接" else "Connected"

val ToolboxStrings.disconnected: String
    get() = if (chinese) "未连接" else "Disconnected"

fun ToolboxStrings.networkTransport(value: NetworkTransport): String = when (value) {
    NetworkTransport.WIFI -> "Wi-Fi"
    NetworkTransport.CELLULAR -> if (chinese) "移动网络" else "Cellular"
    NetworkTransport.ETHERNET -> if (chinese) "以太网" else "Ethernet"
    NetworkTransport.VPN -> "VPN"
    NetworkTransport.BLUETOOTH -> if (chinese) "蓝牙" else "Bluetooth"
    NetworkTransport.OTHER -> if (chinese) "其他" else "Other"
}
