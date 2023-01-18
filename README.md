# Gatling НЦМУ тестирующая система

## Описание

Система предназначена для нагрузочного тестирования jupyterlab, созданного с помощью labelling_server,
а также для тестирования labelling_server.


## Установка

`1)` python3

`2)` Необходимо установить JDK. Например,
```sh
sudo apt install default-jre
```

`3)` Далее необходимо установить [sbt](https://www.scala-sbt.org/download.html)
- Установка для linux (deb):
```sh
echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list
echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | sudo tee /etc/apt/sources.list.d/sbt_old.list
curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add
sudo apt-get update
sudo apt-get install sbt
```
- Установка для linux (rpm):
```sh
# remove old Bintray repo file
sudo rm -f /etc/yum.repos.d/bintray-rpm.repo || true
curl -L https://www.scala-sbt.org/sbt-rpm.repo > sbt-rpm.repo
sudo mv sbt-rpm.repo /etc/yum.repos.d/
sudo yum install sbt
```

`4)` Для использования тестируемых сценариев необходимо просто скопировать репозиторий 
```sh 
git clone https://github.com/danilakovtunn/gatling-jupyter-test
```

`5)` Установка завершена

## Запуск
Для запуска лучше всего использовать скрипт run.py
```sh
usage: python3 run.py [-h] [--test_system {jupyterlab,label_studio,both}]
              [--jaas_ip JAAS_IP] [--port PORT] [--protocol {http,https}]
              [--users USERS] [--ramp RAMP]

options:
  -h, --help            show this help message and exit
  --test_system {jupyterlab,label_studio,both}
                        specify SUT (system under test)
  --jaas_ip JAAS_IP     ip address where Jupyter As A Servise is located
  --port PORT           port to connect to JAAS
  --protocol {http,https}
                        protocol to JAAS
  --users USERS         number of virtual users that will load the server
  --ramp RAMP           given number of users distributed evenly on a time
                        window of a given ramp

```

В результате появится 
- Краткий отчет о выполнении
- Отчет составленный Gatling в виде html-файлов
- Если произошли ошибки, то в директории datetime_errors/ появятся 
-- errors.txt, в котором содержится время работы сессии, для того чтобы проще найти время ошибок в логах, и номер сессии
-- <номер_сессии>.txt, в котором дублируется stdout ошибочной сессии
