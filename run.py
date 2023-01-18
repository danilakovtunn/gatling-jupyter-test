import subprocess
import datetime
import os
import sys
import argparse


def parse_args(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        '--jaas_ip',
        type=str,
        default='10.100.203.110',
        help='ip address where Jupyter As A Servise is located'
    )
    parser.add_argument(
        '--port',
        type=int,
        default=5000,
        help='port to connect to JAAS'
    )
    parser.add_argument(
        '--protocol',
        type=str,
        choices=['http', 'https'],
        default='http',
        help='protocol to JAAS'
    )
    parser.add_argument(
        '--users',
        type=int,
        default=5,
        help='number of virtual users that will load the server'
    )
    parser.add_argument(
        '--ramp',
        type=int,
        default=0,
        help='given number of users distributed evenly on a time window of a given ramp'
    )

    args, args_list = parser.parse_known_args(argv)
    return args.jaas_ip, args.port, args.protocol, args.users, args.ramp


def main():
    argv = sys.argv[1:]
    jaas_url, port, protocol, users, ramp = parse_args(argv)

    start = datetime.datetime.now().isoformat(sep=' ', timespec='seconds')
    pipe = subprocess.Popen(
        ('sbt', 
        '-Djaas_url=' + jaas_url, 
        '-Dport=' + str(port), 
        '-Dprotocol=' + protocol,
        '-Dusers=' + str(users),
        '-Dramp=' + str(ramp), 
        'Gatling/testOnly CreateJupyterlabSimulation'), 
        stdout=subprocess.PIPE
    )
    x = pipe.stdout.readlines()

    for i in x:
        print(i.decode(), end='')

    if len(x[-8].split()) < 3 or x[-8].split()[2] != b'0':
        finish = datetime.datetime.now().isoformat(sep=' ', timespec='seconds')
        error_files = list(filter(lambda x: x[:-4].isdecimal() and x[-4:] == '.txt', os.listdir('datetime_errors')))
        number = len(error_files) + 1
        with open('datetime_errors/' + str(number) + '.txt', 'w') as f:
            for i in x:
                f.write(i.decode())
        with open('datetime_errors/errors.txt', 'a') as f:
            f.write(str(number) + ': ' + start + '     -     ' + finish + '\n')


if __name__ == '__main__':
    main()
