import sys
from pathlib import Path

import jpype
from jdk4py import JAVA_HOME

# the directory where csv.jar is placed
CLASSPATH = Path(__file__).parent

# the list of all .jar files in CLASSPATH
JARS = [str(j.resolve()) for j in CLASSPATH.glob("*.jar")]

# the JVM library shipped by jdk4py, for the current OS
JVM_LIB = {"win32": "jvm.dll", "darwin": "libjli.dylib"}.get(sys.platform, "libjvm.so")
JVM_PATH = str(next(JAVA_HOME.rglob(JVM_LIB)))

jpype.startJVM(jvmpath=JVM_PATH, classpath=JARS)


from .__jvmutils import *
