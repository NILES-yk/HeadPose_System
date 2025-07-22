import urx
import math
import socket
from typing import List, Optional
from urx import Robot, URRobot
import time


yu=0

def connection(robot_ip1):
    rob1=URRobot(robot_ip1)
    return rob1

class URClient:
    def __init__(self,  server_ip: str = '172.20.10.2', server_port: int = 5000):
        # 机器人连接
        #print(f"已连接到UR机器人: {rob2}")

        # 手机服务器配置
        self.server_ip = server_ip
        self.server_port = server_port
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)  # 允许端口复用
        self.joint_angles: List[float] = []

    def connect_to_server(self) -> bool:
        """连接到手机服务端"""
        try:
            print(f"尝试连接到手机服务端 {self.server_ip}:{self.server_port}...")
            self.socket.connect((self.server_ip, int(self.server_port)))
            print("手机连接成功")
            self._send_message("客户端就绪")
            return True
        except ConnectionRefusedError:
            print("连接被拒绝，请确保手机服务端已启动")
            return False
        except Exception as e:
            print(f"连接错误: {str(e)}")
            return False

    def _send_message(self, msg: str) -> None:
        """向服务端发送消息"""
        try:
            self.socket.sendall((msg + "\n").encode('utf-8'))
        except (BrokenPipeError, ConnectionResetError):
            print("与手机的连接已断开")
            self._reconnect()

    def _receive_data(self) -> Optional[str]:
        """从服务端接收数据"""
        self.socket.settimeout(20.0)  # 设置1秒超时
        try:
            data = self.socket.recv(1024).decode('utf-8').strip()
            return data if data else None
        except ConnectionResetError:
            print("手机连接已断开")
            self._reconnect()
            return None
        except socket.timeout:
            return None

    def _reconnect(self) -> None:
        """重新连接服务端"""
        self.socket.close()
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        while not self.connect_to_server():
            print("5秒后尝试重新连接...")
            time.sleep(5)

    def _validate_angle(self, angle_str: str) -> Optional[dict]:
        """验证并解析7个浮点数参数(yaw,pitch,roll,lx,ly,rx,ry)"""
        try:
            #print(angle_str)
            # 分割字符串获取7个参数
            angles = angle_str.split()
            if len(angles) > 7:
                print("输入参数过多，已截断")
                angles = angles[:7]  # 截取前7个参数
            if len(angles) != 7:
                print("输入参数数量错误: 需要7个参数")
                self._send_message("输入数据错误: 需要7个参数(yaw,pitch,roll,lx,ly,rx,ry)\n请重新输入")
                return None

            # 转换为浮点数
            yaw, pitch, roll, lx, ly, rx, ry = map(float, angles)

            # 检查数值范围（这里假设所有参数都在-π到π之间，根据实际需求调整）
            if not all(-180< x < 180 for x in (yaw, pitch, roll)):
                print("角度超出范围: yaw/pitch/roll必须在-π和π之间")
                self._send_message("输入数据错误: yaw/pitch/roll必须介于-π和π之间\n请重新输入")
                return None

            # 检查坐标范围（假设lx,ly,rx,ry在-1到1之间，根据实际需求调整）
            if not all(-1000 <= x <= 1000 for x in (lx, ly, rx, ry)):
                print("坐标超出范围: lx/ly/rx/ry必须在-1到1之间")
                self._send_message("输入数据错误: lx/ly/rx/ry必须介于-1和1之间\n请重新输入")
                return None

            # 返回解析后的参数
            params = {
                'yaw': yaw,
                'pitch': pitch,
                'roll': roll,
                'lx': lx,
                'ly': ly,
                'rx': rx,
                'ry': ry
            }
            print("保存成功:", params)
            self._send_message("保存成功")
            return params

        except ValueError:
            print("无效的输入: 必须输入7个有效的数字")
            self._send_message("输入数据错误: 必须输入7个有效的数字\n请重新输入")
            return None

    def run(self) -> Optional['dict']|None:
        """主运行循环"""
        global yu
        if yu==0:
            self.connect_to_server()
            yu+=1
        i=0
        if yu==1:
            try:
                while i<1: #先不让while进入死循环
                    # 等待服务端提示
                    print("等待服务端发送关节角度...")
                    #self._send_message("请发送关节角度(-π到π之间)")

                    # 接收角度数据
                    data = self._receive_data()
                    #print(data)
                    if data is None:
                        continue

                    # 验证并存储角度
                    parameter = self._validate_angle(data)
                    print(parameter)
                    i+=1
                    return parameter
            except KeyboardInterrupt:
                  print("\n客户端关闭中...")
            finally:
                print('OK')
            #self._cleanup()


    def _move_robot(self) -> None:
        """控制机器人移动"""
        print(f"执行机器人运动: {self.joint_angles}")
        try:
            #elf.rob.movej(self.joint_angles, acc=0.1, vel=0.1)
            self._send_message("机器人运动指令执行成功")
        except Exception as e:
            print(f"机器人运动错误: {str(e)}")
            self._send_message(f"机器人运动错误: {str(e)}")
        finally:
            self.joint_angles.clear()

    def _cleanup(self) -> None:
        """清理资源"""
        self.socket.close()
        #self.rob.close()
        print("连接已关闭")


if __name__ == "__main__":
    #配置参数
    ROBOT_IP = '192.168.197.133'  # UR机器人IP
    SERVER_IP = "113.54.213.37"  # 手机IP地址
    SERVER_PORT = 5000 # 手机服务端口
    rob=connection(ROBOT_IP)
    # 创建并运行客户端
    client = URClient(SERVER_IP, SERVER_PORT)
    parameter=client.run()
