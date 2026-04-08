@echo off
cd /d "d:\git hub\tally_integration\backend"

REM Download GSON if not present
if not exist "lib" mkdir lib
if not exist "lib\gson-2.10.1.jar" (
    echo Downloading GSON...
    powershell -Command "(New-Object System.Net.WebClient).DownloadFile('https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar', 'lib\gson-2.10.1.jar')"
)

REM Compile Java files
if not exist "build" mkdir build
echo Compiling...
javac -d build -cp "lib\gson-2.10.1.jar" src\main\java\com\tally\*.java

REM Run the server
echo Starting server...
java -cp "build;lib\gson-2.10.1.jar" com.tally.App

pause