JDK_HOME := /usr/local/Cellar/openjdk/23.0.1
CPP := clang++
CPP_HOME := src/main/native/cpp
CPP_FILES := $(CPP_HOME)/*.cpp
CPP_FLAGS := -std=c++11 -O3 -I$(CPP_HOME)/../include -I"$(JDK_HOME)/include"
LIB_NAME := libjni_stream_enhancer.dylib
SHARED_TARGET := target/classes/$(LIB_NAME)

CPU_FEATURES := $(shell sysctl -a | grep machdep.cpu.features)
ifneq ($(findstring AVX2,$(CPU_FEATURES)),)
    CPP_FLAGS += -mavx2 -D__AVX2__
endif
ifneq ($(findstring SSE4.1,$(CPU_FEATURES)),)
    CPP_FLAGS += -msse4.1 -D__SSE4_1__
endif

build: $(SHARED_TARGET)

$(SHARED_TARGET): $(CPP_FILES)
	mkdir -p target/classes
	$(CPP) -shared -fPIC $(CPP_FLAGS) -o $@ $^

clean:
	rm -f $(SHARED_TARGET)
	mvn clean

.PHONY: build clean
