JDK_HOME := /usr/local/Cellar/openjdk/23.0.1
CPP := clang++
CPP_OPTS := -std=c++11 -O3
CPP_HOME := cpp
SHARED_TARGET := $(CPP_HOME)/libjni_stream_enhancer.dylib

CPU_FEATURES := $(shell sysctl -a | grep machdep.cpu.features)
ifneq ($(findstring AVX2,$(CPU_FEATURES)),)
    CPP_OPTS += -mavx2 -D__AVX2__
endif
ifneq ($(findstring SSE4.1,$(CPU_FEATURES)),)
    CPP_OPTS += -msse4.1 -D__SSE4_1__
endif

build:
	$(CPP) -shared -fPIC -I"$(JDK_HOME)/include" -I"$(JDK_HOME)/include/darwin" $(CPP_OPTS) -o $(SHARED_TARGET) $(CPP_HOME)/*.cpp

clean:
	rm -f $(SHARED_TARGET)
	mvn clean
