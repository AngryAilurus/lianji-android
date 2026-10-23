"""Build using the installed Android SDK, without downloading dependencies."""
import os, subprocess, pathlib, zipfile, shutil
ROOT=pathlib.Path(__file__).resolve().parent
SDK=pathlib.Path(os.environ.get('ANDROID_HOME',str(pathlib.Path.home()/'Library/Android/sdk')))
JAVA=pathlib.Path(os.environ.get('JAVA_HOME','/Applications/Android Studio.app/Contents/jbr/Contents/Home'))
BT=SDK/'build-tools/36.0.0'; ANDROID=SDK/'platforms/android-36/android.jar'
BUILD=ROOT/'build'; BUILD.mkdir(exist_ok=True)
ENV=dict(os.environ,JAVA_HOME=str(JAVA),PATH=str(JAVA/'bin')+os.pathsep+os.environ['PATH'])
def run(*args): subprocess.run([str(x) for x in args],cwd=ROOT,env=ENV,check=True)
run(BT/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',BUILD/'resources.zip')
run(BT/'aapt2','link','-o',BUILD/'base.apk','--manifest',ROOT/'app/src/main/AndroidManifest.xml','-I',ANDROID,'--java',BUILD/'generated','-A',ROOT/'app/src/main/assets',BUILD/'resources.zip')
(BUILD/'classes').mkdir(exist_ok=True); (BUILD/'dex').mkdir(exist_ok=True)
sources=list((ROOT/'app/src/main/java').rglob('*.java'))+list((BUILD/'generated').rglob('*.java'))
run(JAVA/'bin/javac','-encoding','UTF-8','-source','8','-target','8','-classpath',ANDROID,'-d',BUILD/'classes',*sources)
run(BT/'d8','--lib',ANDROID,'--min-api','26','--output',BUILD/'dex',*list((BUILD/'classes').rglob('*.class')))
shutil.copy(BUILD/'base.apk',BUILD/'unsigned.apk')
with zipfile.ZipFile(BUILD/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as z:
    for p in (BUILD/'dex').glob('*.dex'): z.write(p,p.name)
run(BT/'zipalign','-f','4',BUILD/'unsigned.apk',BUILD/'aligned.apk')
key=ROOT/'signing.jks'
if not key.exists(): run(JAVA/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','lianji','-dname','CN=Lianji Personal App','-keyalg','RSA','-keysize','2048','-validity','10000')
out=pathlib.Path(os.environ.get('LIANJI_APK_OUTPUT',str(ROOT/'dist/练记-1.3.0.apk'))).resolve()
out.parent.mkdir(parents=True,exist_ok=True)
run(BT/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',out,BUILD/'aligned.apk')
run(BT/'apksigner','verify','--verbose',out)
print(out)
