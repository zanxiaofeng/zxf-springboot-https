# FLClash 系统代理与 TUN 模式原理及排查手册

> 适用：FLClash / Clash Meta (mihomo) 在 Linux 桌面及服务器环境下的代理模式理解与故障排查

---

## 目录

1. [概述](#1-概述)
2. [系统代理模式](#2-系统代理模式)
   - 2.1 原理
   - 2.2 工作流程
   - 2.3 局限性
   - 2.4 问题排查
3. [TUN 模式](#3-tun-模式)
   - 3.1 原理
   - 3.2 工作流程
   - 3.3 与系统代理对比
   - 3.4 问题排查
4. [模式决策速查表](#4-模式决策速查表)
5. [附录：常用诊断命令](#5-附录常用诊断命令)

---

## 1. 概述

FLClash 提供两种核心流量接管机制：

| 机制 | 层级 | 强制性 | 本质 |
|------|------|--------|------|
| **系统代理** | 应用层 | 建议性，应用可忽略 | 操作系统配置中心 |
| **TUN 模式** | 网络层（IP 层） | 强制性，内核转发 | 虚拟网卡 + 路由劫持 |

理解两者的差异是排查"代理不生效"问题的关键。

---

## 2. 系统代理模式

### 2.1 原理

系统代理并非网络层拦截，而是操作系统提供的**配置中心**。FLClash 将代理地址写入系统配置后，由**应用自行决定是否读取并遵守**。

```
┌─────────────────────────────────────────┐
│           操作系统（配置层）              │
│  ┌─────────────┐  ┌─────────────────┐   │
│  │  gsettings   │  │  注册表/系统偏好   │   │
│  │  (GNOME)     │  │  (Windows/macOS) │   │
│  └──────┬──────┘  └────────┬────────┘   │
│         │                  │             │
│  "HTTP代理: 127.0.0.1:7890" │            │
│         │                  │             │
└─────────┼──────────────────┼─────────────┘
          │                  │
          ▼                  ▼
┌─────────────────────────────────────────┐
│              应用程序层                  │
│                                         │
│  Chrome ──► 读取系统代理？ 是 ──► 走代理  │
│  Firefox ──► 读取系统代理？ 否 ──► 忽略   │
│  curl ──► 读取环境变量？ 是 ──► 走代理    │
│  微信 ──► 读取系统代理？ 否 ──► 直连      │
│                                         │
└─────────────────────────────────────────┘
```

### 2.2 工作流程（Linux GNOME）

1. **写入配置**：FLClash 调用 `gsettings set org.gnome.system.proxy mode 'manual'`
2. **持久化**：配置写入 `~/.config/dconf/user` 二进制数据库
3. **读取**：应用启动时通过 `libproxy` 或 `GSettings API` 读取
4. **决策**：应用自行决定是否使用这些代理值发起连接

### 2.3 局限性

| 局限 | 说明 |
|------|------|
| **应用兼容性** | Firefox 默认使用自身代理设置；微信等国产软件通常完全忽略系统代理 |
| **桌面环境依赖** | 仅 GNOME/GTK 应用默认读取；KDE/Qt 读取 KConfig；i3wm/Sway 等平铺管理器无此概念 |
| **命令行工具** | `curl`、`wget`、`git` 不读取 gsettings，只认环境变量 `http_proxy` |
| **协议覆盖不全** | 系统代理通常只配 HTTP/HTTPS，SOCKS5/UDP 需额外配置 |
| **权限隔离** | 以 `sudo` 运行的应用读取的是 root 用户的 gsettings，与当前用户配置隔离 |

### 2.4 问题排查

#### 步骤 1：确认 FLClash 内核工作正常

```bash
# 测试代理端口是否监听并响应
curl -x http://127.0.0.1:7890 https://www.google.com/generate_204
# 返回 204 说明内核正常；不通则检查订阅和节点
```

#### 步骤 2：检查系统代理是否写入

```bash
# GNOME 桌面环境
gsettings get org.gnome.system.proxy mode
# 预期返回：'manual'

gsettings get org.gnome.system.proxy.http host
gsettings get org.gnome.system.proxy.http port
# 预期：127.0.0.1 / 7890（或你设置的端口）

# 查看完整配置
gsettings list-recursively org.gnome.system.proxy
```

#### 步骤 3：修复写入失败

```bash
# 手动写入 GNOME 代理配置
gsettings set org.gnome.system.proxy mode 'manual'
gsettings set org.gnome.system.proxy.http host '127.0.0.1'
gsettings set org.gnome.system.proxy.http port 7890
gsettings set org.gnome.system.proxy.https host '127.0.0.1'
gsettings set org.gnome.system.proxy.https port 7890
gsettings set org.gnome.system.proxy.socks host '127.0.0.1'
gsettings set org.gnome.system.proxy.socks port 7897
gsettings set org.gnome.system.proxy ignore-hosts "['localhost', '127.0.0.0/8', '::1']"
```

如果报错 `dconf-WARNING **: failed to commit changes`：
```bash
# 修复 dconf 权限
sudo chown -R $(whoami):$(whoami) ~/.config/dconf
```

#### 步骤 4：应用级单独配置

对于不读取系统代理的应用：

```bash
# 环境变量（写入 ~/.bashrc 或 ~/.zshrc）
export http_proxy="http://127.0.0.1:7890"
export https_proxy="http://127.0.0.1:7890"
export all_proxy="socks5://127.0.0.1:7897"
export no_proxy="localhost,127.0.0.1,::1"

# Chrome 启动参数
chromium --proxy-server="http://127.0.0.1:7890"

# Git
git config --global http.proxy http://127.0.0.1:7890
git config --global https.proxy http://127.0.0.1:7890
```

#### 步骤 5：确认应用是否读取

打开 `https://ip.sb` 查看出口 IP：
- 显示代理节点 IP → 系统代理生效
- 显示本地 IP → 该应用未读取系统代理

---

## 3. TUN 模式

### 3.1 原理

TUN 模式在操作系统内核层创建**虚拟网卡**（如 `utun`、`Meta`、`flclash`），并通过修改系统路由表将所有 IP 数据包强制引入该网卡。Clash 内核在用户态接管这些数据包，进行分流和代理转发。

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   应用层     │────►│  操作系统    │────►│  物理网卡    │
│  (浏览器等)  │     │  (TCP/IP栈) │     │  (eth0/wlan0)│
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │
                    ┌──────┴──────┐
                    │   TUN 虚拟网卡 │
                    │  (utun/Meta)  │
                    │  强制拦截所有  │
                    │   IP 数据包   │
                    └──────┬──────┘
                           ▼
                    ┌─────────────┐
                    │  Clash 内核  │
                    │  (路由/分流) │
                    └─────────────┘
```

**核心特点**：不依赖任何应用"是否读取代理配置"，在内核层强制转发。

### 3.2 工作流程

1. **创建虚拟网卡**：FLClash 请求内核创建 TUN 设备（如 `/dev/net/tun`）
2. **配置 IP 地址**：为虚拟网卡分配 IP（如 `198.18.0.1/30`）
3. **修改路由表**：添加 `0.0.0.0/1` 和 `128.0.0.0/1` 路由指向 TUN 网卡（比默认路由 `0.0.0.0/0` 更精确，优先匹配）
4. **劫持 DNS**：将系统 DNS 指向 TUN 网卡地址，由 Clash 内核解析
5. **数据包处理**：所有出站 IP 包进入 TUN → Clash 内核按 Rule 分流 → 直连或经代理节点转发

### 3.3 与系统代理对比

| 特性 | 系统代理 | TUN 模式 |
|------|---------|---------|
| **层级** | 应用层 | 网络层（IP 层） |
| **强制性** | ❌ 应用可忽略 | ✅ 内核强制转发 |
| **覆盖范围** | 浏览器等支持代理的应用 | 所有 TCP/UDP 流量 |
| **UDP 支持** | 依赖应用支持 | 原生支持（游戏、视频通话） |
| **ICMP (ping)** | 通常不走代理 | 可走代理 |
| **权限要求** | 普通用户 | root / CAP_NET_ADMIN |
| **性能** | 无额外开销 | 增加一次用户态拷贝 |
| **兼容性** | 依赖桌面环境和应用 | 几乎所有 Linux 系统 |

### 3.4 问题排查

#### 步骤 1：确认权限

```bash
# 查看 FLClash 进程用户
ps aux | grep -i flclash

# 确认 capabilities
getcap $(which FlClash)
# 预期：cap_net_admin,cap_net_bind_service=ep
```

**修复权限**：
```bash
# 方案 A：全程 root（不推荐日常使用）
sudo FlClash

# 方案 B：添加 capabilities（推荐）
sudo setcap cap_net_admin,cap_net_bind_service=+ep $(which FlClash)
# AppImage 用户需指定完整路径
sudo setcap cap_net_admin,cap_net_bind_service=+ep /path/to/FlClash.AppImage
```

#### 步骤 2：检查 TUN 设备节点

```bash
ls -la /dev/net/tun
# 预期：存在该字符设备

# 检查内核模块
lsmod | grep tun
# 预期：输出 tun 模块信息

# 如果不存在，加载并持久化
sudo modprobe tun
echo "tun" | sudo tee /etc/modules-load.d/tun.conf
```

#### 步骤 3：检查虚拟网卡是否创建

```bash
ip addr show
# 预期看到：utun、Meta、flclash、clash0 等接口

# 查看接口详情
ip addr | grep -E "(utun|Meta|flclash|clash)"
```

**如果没有接口**：检查 FLClash 日志中是否有 `create tun:` 报错。

#### 步骤 4：检查路由表

```bash
ip route show
# 预期看到 TUN 相关路由：
# 0.0.0.0/1 dev utun
# 128.0.0.0/1 dev utun
```

**路由未添加的常见原因**：
- 权限不足（`ip route` 需要 root）
- 路由冲突（其他 VPN/Docker 已占用）

**排查冲突**：
```bash
ip route | grep -E "(tun|wg|docker|vpn)"
nmcli device status
```

**手动测试添加路由**：
```bash
sudo ip route add 0.0.0.0/1 dev utun
sudo ip route add 128.0.0.0/1 dev utun
```

#### 步骤 5：检查防火墙

```bash
# iptables
sudo iptables -L -n -v
sudo iptables -t nat -L -n -v

# ufw
sudo ufw status

# firewalld
sudo firewall-cmd --state
```

**临时关闭测试**（测试后务必恢复）：
```bash
sudo ufw disable
# 或
sudo systemctl stop firewalld
```

**放行 TUN 接口**（如确认防火墙导致）：
```bash
sudo ufw allow in on utun
sudo ufw allow out on utun
```

#### 步骤 6：检查 DNS 劫持

TUN 模式通常需要覆写 DNS，否则出现"能 ping 通 IP 但打不开网页"。

```bash
# 查看当前 DNS
cat /etc/resolv.conf

# TUN 开启后，FLClash 通常将 DNS 指向 127.0.0.1 或虚拟网卡地址

# 测试 DNS 解析
dig @127.0.0.1 google.com
nslookup google.com 127.0.0.1

# 检查 DNS 端口占用
sudo ss -tlnp | grep -E ":53|:1053"
```

**修复**：在 FLClash 设置中确认开启 **"覆写 DNS"** 或 **"使用系统 hosts"**。

#### 步骤 7：一键诊断脚本

```bash
#!/bin/bash
echo "=== FLClash TUN 诊断 ==="
echo ""

echo "1. 进程与权限:"
ps aux | grep -i flclash | grep -v grep
echo ""

echo "2. TUN 设备节点:"
ls -la /dev/net/tun 2>/dev/null || echo "❌ /dev/net/tun 不存在"
echo ""

echo "3. TUN 内核模块:"
lsmod | grep tun || echo "❌ tun 模块未加载"
echo ""

echo "4. 网络接口:"
ip addr | grep -E "(utun|Meta|flclash|clash)" || echo "❌ 无 TUN 接口"
echo ""

echo "5. 路由表:"
ip route | grep -E "(utun|Meta|flclash|0\.0\.0\.0/1|128\.0\.0\.0/1)" || echo "❌ 无 TUN 路由"
echo ""

echo "6. DNS 配置:"
cat /etc/resolv.conf | grep nameserver
echo ""

echo "7. 防火墙状态:"
sudo ufw status 2>/dev/null || echo "ufw 未安装/未启用"
sudo iptables -L -n | grep -i reject | head -5
echo ""

echo "8. 端口监听:"
sudo ss -tlnp | grep -E "(7890|7897|53|1053)" || echo "无相关端口监听"
echo ""

echo "=== 诊断结束 ==="
```

#### 步骤 8：系统级 TUN 能力测试

如果 FLClash 始终无法创建 TUN，测试系统本身能力：

```bash
# 安装工具
sudo apt install uml-utilities

# 手动创建 TUN 接口
sudo tunctl -t testtun
sudo ip addr add 10.0.0.1/24 dev testtun
sudo ip link set testtun up
ip addr show testtun

# 清理
sudo tunctl -d testtun
```

如果手动创建也失败，说明**系统内核或权限配置有问题**，与 FLClash 无关。

---

## 4. 模式决策速查表

| 使用场景 | 推荐模式 | 说明 |
|---------|---------|------|
| Ubuntu/GNOME 桌面日常浏览 | **系统代理** | 性能最好，浏览器原生支持 |
| i3wm / Sway / XFCE / 无桌面 | **TUN** | 这些环境无 gsettings 代理概念 |
| 代理游戏 / Discord / Zoom | **TUN** | UDP 流量必须走网络层 |
| 代理企业软件 / 硬编码应用 | **TUN** | 应用层代理对这些无效 |
| 手机/其他设备共享电脑代理 | **Allow LAN** | 配合系统代理或 TUN 使用 |
| 服务器 / 远程主机长期运行 | **TUN + 服务** | 后台常驻，所有流量接管 |
| 开发测试节点连通性 | **Global + TUN** | 强制所有流量走单一节点 |
| 需要 `ping 8.8.8.8` 也走代理 | **TUN** | ICMP 只能走网络层 |

---

## 5. 附录：常用诊断命令

### 代理连通性测试

```bash
# 测试 HTTP 代理端口
curl -x http://127.0.0.1:7890 https://www.google.com/generate_204

# 测试 SOCKS5 端口
curl -x socks5://127.0.0.1:7897 https://www.google.com/generate_204

# 测试 TUN 是否生效（不走 -x 参数，看系统路由是否引入 TUN）
curl https://www.google.com/generate_204
```

### 路由与网络

```bash
ip addr show                    # 查看所有接口
ip route show                   # 查看路由表
ip route get 8.8.8.8            # 查看某目的地址走哪个接口
ss -tlnp                        # 查看监听端口
sudo lsof -i :7890              # 查看端口占用进程
```

### 权限与能力

```bash
getcap $(which FlClash)         # 查看 capabilities
sudo setcap cap_net_admin,cap_net_bind_service=+ep <path>  # 添加能力
sudo getcap <path>              # 验证
```

### DNS 诊断

```bash
dig @127.0.0.1 google.com       # 测试本地 DNS
nslookup google.com 127.0.0.1   # 同上
cat /etc/resolv.conf            # 查看系统 DNS
systemd-resolve --status        # systemd-resolved 状态
```

### 日志查看

```bash
# 终端前台启动，观察实时日志
FlClash

# 或查看 systemd 服务日志（如安装了服务）
journalctl -u flclash -f
```

---

> **核心结论**：系统代理是操作系统对应用的"建议"，TUN 模式是内核对流量的"强制"。当系统代理不生效时，切换到 TUN 模式是最彻底的解决方案；当 TUN 不工作时，优先排查**权限 → TUN 设备 → 路由表 → 防火墙 → DNS** 五个环节。
