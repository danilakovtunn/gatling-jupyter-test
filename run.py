import subprocess
import datetime
import os


start = datetime.datetime.now().isoformat(sep=' ', timespec='seconds')
pipe = subprocess.Popen(
    ('sbt', 
    '-Djaas_url=10.100.203.110', 
    '-Dport=5000', 
    '-Dprotocol=http',
    '-Dusers=5',
    '-Dramp=30', 
    'Gatling/test'), 
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
