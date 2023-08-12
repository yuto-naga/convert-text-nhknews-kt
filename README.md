# convert-text-nhknews-kt

##### 動作環境

- MacBook Pro M1
- OS Venture 13.4.1

##### 環境準備

- seleniarm(chromium)コンテナ起動

```console
%  docker run --rm -it -p 4444:4444 -p 5900:5900 -p 7900:7900 --shm-size 2g seleniarm/standalone-chromium:latest
```

- voicevoxコンテナ起動

```
% docker run --rm -it -p '127.0.0.1:50021:50021' voicevox/voicevox_engine:cpu-ubuntu20.04-latest
```