#!/usr/bin/env python3
"""Manda un comando a la consola del server por RCON.  Uso: mc "whitelist add Nombre" """
import re
import socket
import struct
import sys

PROPS = '/opt/minecraft/server.properties'


def leer_props():
    props = {}
    with open(PROPS, encoding='utf-8') as f:
        for linea in f:
            if '=' in linea and not linea.startswith('#'):
                k, v = linea.rstrip('\n').split('=', 1)
                props[k] = v
    return props


def enviar(sock, rid, tipo, cuerpo):
    datos = struct.pack('<ii', rid, tipo) + cuerpo.encode('utf-8') + b'\x00\x00'
    sock.sendall(struct.pack('<i', len(datos)) + datos)


def exacto(sock, n):
    b = b''
    while len(b) < n:
        parte = sock.recv(n - len(b))
        if not parte:
            raise ConnectionError('RCON cerrado')
        b += parte
    return b


def leer(sock):
    largo = struct.unpack('<i', exacto(sock, 4))[0]
    datos = exacto(sock, largo)
    rid, _ = struct.unpack('<ii', datos[:8])
    return rid, datos[8:-2].decode('utf-8', 'replace')


def main():
    if len(sys.argv) < 2:
        print('Uso: mc "comando"')
        sys.exit(1)
    p = leer_props()
    with socket.create_connection(('127.0.0.1', int(p.get('rcon.port', 25575))), timeout=10) as s:
        enviar(s, 1, 3, p['rcon.password'])
        if leer(s)[0] == -1:
            print('Clave RCON incorrecta')
            sys.exit(1)
        enviar(s, 2, 2, ' '.join(sys.argv[1:]))
        print(re.sub(r'§.', '', leer(s)[1]))


if __name__ == '__main__':
    main()
