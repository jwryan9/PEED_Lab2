import socket

UDP_IP = '::1'  # localhost
UDP_PORT = 5005
MESSAGE = 'Hello World :('

print('UDP target IP:', UDP_IP)
print('UDP target port:', UDP_PORT)
print('message:', MESSAGE)

sock = socket.socket(socket.AF_INET6, socket.SOCK_DGRAM)
sock.sendto(MESSAGE, (UDP_IP, UDP_PORT))
