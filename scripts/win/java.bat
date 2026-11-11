@echo off
rem Busca un Java 25 o superior y lo deja en la variable JAVA (ruta a java.exe).
rem Orden: AS2_JAVA_HOME, JAVA_HOME, el JDK de Maven (toolchains.xml), carpetas de instalacion
rem habituales y el PATH.
rem Cada candidato se prueba con "java -version": los JAR estan compilados para Java 25.
rem Uso: call scripts\win\java.bat   (errorlevel 1 si no hay Java 25)
set "JAVA="
rem start.bat ya lo busco y lo pasa en AS2_JAVA a cada ventana: no se vuelve a buscar.
if not defined AS2_JAVA goto :buscar
if not exist "%AS2_JAVA%" goto :buscar
set "JAVA=%AS2_JAVA%"
exit /b 0

:buscar
if defined AS2_JAVA_HOME call :probar "%AS2_JAVA_HOME%\bin\java.exe"
if not defined JAVA if defined JAVA_HOME call :probar "%JAVA_HOME%\bin\java.exe"
rem El JDK que Maven usa para compilar (toolchains) tambien sirve para ejecutar.
rem "tokens=*" quita la sangria; luego <jdkHome>RUTA</jdkHome> se parte por los signos < y >.
if not defined JAVA if exist "%USERPROFILE%\.m2\toolchains.xml" for /f "tokens=*" %%L in ('findstr /i "<jdkHome>" "%USERPROFILE%\.m2\toolchains.xml"') do (
  for /f "tokens=2 delims=<>" %%H in ("%%L") do if not defined JAVA call :probar "%%~H\bin\java.exe"
)
if not defined JAVA for /d %%D in ("%ProgramFiles%\Java\jdk-2*" "%ProgramFiles%\Eclipse Adoptium\jdk-2*" "%ProgramFiles%\Microsoft\jdk-2*" "%ProgramFiles%\Zulu\zulu-2*" "%ProgramFiles%\Amazon Corretto\jdk2*" "%USERPROFILE%\.jdks\*") do (
  if not defined JAVA call :probar "%%~fD\bin\java.exe"
)
if not defined JAVA for /f "delims=" %%J in ('where java 2^>nul') do (
  if not defined JAVA call :probar "%%~fJ"
)
if defined JAVA exit /b 0
echo.
echo ERROR: no se encontro Java 25 o superior.
echo   Los servicios estan compilados para Java 25. Instala un JDK 25 o indica donde esta:
echo     set AS2_JAVA_HOME=C:\ruta\al\jdk-25
echo   (o define AS2_JAVA_HOME en las variables de entorno de Windows).
exit /b 1

:probar
if not exist "%~1" exit /b 0
set "AS2_JMAJOR=0"
rem Archivo propio de esta ventana: varias ventanas pueden buscar a la vez.
rem %RANDOM% se repite en ventanas abiertas en el mismo segundo: se le suma la hora con centesimas.
set "AS2_T=%TIME::=%"
set "AS2_T=%AS2_T:,=%"
set "AS2_T=%AS2_T:.=%"
set "AS2_T=%AS2_T: =0%"
set "AS2_JVER=%TEMP%\as2-java-%AS2_T%-%RANDOM%.txt"
"%~1" -version 2>"%AS2_JVER%" >nul
for /f "usebackq tokens=3" %%V in (`findstr /i /c:" version " "%AS2_JVER%"`) do (
  for /f "delims=." %%M in ("%%~V") do set "AS2_JMAJOR=%%M"
)
del "%AS2_JVER%" >nul 2>&1
if %AS2_JMAJOR% GEQ 25 goto :aceptar
rem Fuera de parentesis: una ruta como "Program Files (x86)" no rompe el echo.
echo   Se descarta "%~1" ^(Java %AS2_JMAJOR%^)
exit /b 0

:aceptar
set "JAVA=%~1"
exit /b 0
