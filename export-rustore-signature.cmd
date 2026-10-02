@echo off
setlocal
chcp 65001 >nul
set "JAVA=C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot\bin\java.exe"
set "PEPK=C:\Users\antro\AndroidStudioProjects\MMover\pepk.jar"
set "KEYSTORE=C:\Siniy\Apps\Key\Key69.jks"
set "OUTPUT=%~dp0pepk_out.zip"
if not exist "%JAVA%" goto missing
if not exist "%PEPK%" goto missing
if not exist "%KEYSTORE%" goto missing
if exist "%OUTPUT%" (
  echo Output already exists: "%OUTPUT%"
  echo Existing archive will not be overwritten.
  pause
  exit /b 1
)
echo Exporting existing key: mouse-mover-alias
echo Enter passwords only in this window. Typed passwords may be invisible.
echo No files will be uploaded by this script.
"%JAVA%" -jar "%PEPK%" --keystore "%KEYSTORE%" --alias mouse-mover-alias --output "%OUTPUT%" --encryptionkey=0000625748cd513c8db54094b0b906109be112fb380b023a1b5567b4c343e52fa25f01b604a298f98a84dd958adc39e7bf86d94f0a592efc1e344f11b8da525db9fe1c64 --include-cert
if errorlevel 1 (
  echo Export failed. Do not upload any incomplete output file.
  pause
  exit /b 1
)
if not exist "%OUTPUT%" (
  echo Export did not produce the expected archive.
  pause
  exit /b 1
)
echo.
echo Export completed: "%OUTPUT%"
echo Upload ZIP as the app signing key archive.
echo Upload mmover-upload-certificate.pem as the upload key certificate.
pause
exit /b 0
:missing
echo Required Java, pepk.jar or keystore file was not found.
pause
exit /b 1
