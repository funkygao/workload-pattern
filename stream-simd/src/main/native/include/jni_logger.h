#ifndef JNI_LOGGER_H
#define JNI_LOGGER_H

#include <fstream>
#include <iostream>

class JniLogger {
public:
    static std::ofstream& get() {
        static std::ofstream instance("/tmp/jni_output.log", std::ios_base::app);
        return instance;
    }
};

#define jni_cout JniLogger::get() << std::flush

#endif // JNI_LOGGER_H
