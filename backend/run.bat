@echo off
cd /d "d:\git hub\tally_integration\backend"

REM Download GSON if not present
if not exist "lib" mkdir lib
if not exist "lib\gson-2.10.1.jar" (
    echo Downloading GSON...
    powershell -Command "(New-Object System.Net.WebClient).DownloadFile('https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar', 'lib\gson-2.10.1.jar')"
)

REM Download Apache POI if not present
if not exist "lib\poi-5.2.4.jar" (
    echo Downloading POI...
    powershell -Command "(New-Object System.Net.WebClient).DownloadFile('https://repo1.maven.org/maven2/org/apache/poi/poi/5.2.4/poi-5.2.4.jar', 'lib\poi-5.2.4.jar')"
)
if not exist "lib\poi-ooxml-5.2.4.jar" (
    echo Downloading POI OOXML...
    powershell -Command "(New-Object System.Net.WebClient).DownloadFile('https://repo1.maven.org/maven2/org/apache/poi/poi-ooxml/5.2.4/poi-ooxml-5.2.4.jar', 'lib\poi-ooxml-5.2.4.jar')"
)

REM Compile Java files
if not exist "build" mkdir build
echo Compiling...
javac -d build -cp "lib\*" src\main\java\com\tally\*.java

REM Run the server
echo Starting server...
java -cp "build;lib\*" com.tally.App

pause
