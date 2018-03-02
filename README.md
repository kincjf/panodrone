>Note: 이 앱은 Android SDK 7.1.1 및 DJI SDK 4.4.1, OpenCV SDK 3.4.0 이 사용되었습니다.
>
# Panom for DJI
>이 프로젝트는 DJI의 Github Forum 에서 많은 부분 도움을 받았으며,
>
>Mavic Pro 및 Inspire1 에서의 테스트가 완료되었습니다.

## 기본 설정 및 실행환경 (Default settings and Environment)

이 어플리케이션을 사용하기 위해 필요한 SDK는 아래와 같습니다.
1. Android Studio(버젼 3.0.0 이상) + SDK Bulid-Tools
2. CMake (NDK 사용을 위한)
3. Android SDK Tools
4. Google Play Services
5. Android NDK(Native Development Kit)

![Alt text](/sdk.png)

또한 CMakeList.txt 의 절대경로에 대한 수정이 필요합니다.
<pre><code>
...
cmake_minimum_required(VERSION 3.4.1)

set(pathOPENCV C:/OpenCV-android-sdk)
set(pathPROJECT C:/Users/user/Desktop/Project/Panom)
set(pathLIBOPENCV_JAVA ${pathPROJECT}/app/src/main/JniLibs/${ANDROID_ABI}/libopencv_java3.so)
...
</code></pre>

app의 build gradle 에서 추가되는 도큐먼트는 아래와 같습니다.
각각 DJI SDK의 사용, RC UI의 Google Maps의 사용,

안드로이드 Buttom Navigation View의 사용을 위한 구성요소입니다.

<pre><code>
...
compile 'com.dji:dji-sdk:4.4.1'
compile 'com.dji:dji-uilibrary:4.4.1'
provided 'com.dji:dji-sdk-provided:4.4.1'

compile 'com.google.android.gms:play-services-maps:10.2.6'
compile 'com.google.android.gms:play-services-location:10.2.6'
compile 'com.google.android.gms:play-services-places:10.2.6'

compile "com.android.support:design:25.0.0"

implementation project(':openCVLibrary340')
...
</code></pre>

위의 SDK를 사용하기 위해서는 api key를 발급받아야 합니다.

Android Manifest 의 application 단에 아래의 값을 정의하십시오.
아래의 링크에서 발급가능합니다.

><https://console.developers.google.com/cloud-resource-manager/>
>
><https://developer.dji.com/user/apps/>

<pre><code>
...
&ltmeta-data
	android:name="com.dji.sdk.API_KEY"
	android:value="YOUR KEY.." />
&ltmeta-data
	android:name="com.google.android.geo.API_KEY"
	android:value="YOUR KEY.." />
...
</code></pre>

## 액티비티 및 기능설명 (Description of Activities and Methods)
>액티비티간의 연결은 안드로이드 디자인의 Buttom Navigation View 를 이용합니다.
>
>BottomNavigationView.OnNavigationItemSelectedListener 를 인터페이스 상속을 통해 각각의 액티비티에 부여합니다.
###0. SplashActivity
어플리케이션의 로딩화면입니다.

SDK Permission 과 관련된 시행사항의 일부를 감춰주며, 3000ms 간 띄워집니다.

### 1. ConnectionActivity
어플리케이션의 기본화면입니다.

DJI SDK의 app key에 따른 인증과정 및 Click Listener를 이용한 _MainActivity_ 로의 인텐트간의 연결을 실행합니다.

##### 1.1 MainActivity
DJI RC(Remote Controller)와의 연동과정이 들어간 액티비티입니다.

핵심 구성요소는 Fragment 로서 정의되어있으며, __FPVFragment__ 및 __MapsFragment__ 라는 이름으로 정의되어있습니다.

각각 FPV 카메라부분과 Google Maps를 독립적으로 띄워줍니다.

### 2. ImageActivity
어플리케이션의 중간 액티비티입니다. 

드론의 SD카드에 대한 접근과 미디어 다운로드가 가능한 _FileActivity_ 로의 인텐트간의 연결을 실행합니다.
또한 파노라마 스티칭을 지원하며, 2개의 버튼을 통해 __Source Image Select__ 및 __Image Stitching__ 을 실행합니다.

#### 2.1 FileActivity
드론에서 미디어를 불러와 디스플레이에 보여주며 다운로드, 삭제 등의 활동을 할 수 있습니다.

자세한 사항은 아래의 DJI SDK 레퍼런스 문서를 참조해주세요.

><https://github.com/DJI-Mobile-SDK-Tutorials/Android-MediaManagerDemo>

#### 2.2 native-lib
안드로이드 어플리케이션 내에서의 파노라마 스티칭을 활용하기 위해선

네이티브 C++ 소스코드를 활용한 OpenCV 의 이용이 필요합니다.

아래의 핵심 소스코드가 파노라마 스티칭을 진행합니다.
<pre><code>
...
Stitcher stitcher = Stitcher::createDefault(try_use_gpu);
Stitcher::Status status = stitcher.stitch(imgs, pano);
<br>
//error occurs
if(status != Stitcher::OK)
	return (int)status;
...
</code></pre>

### 3. InfoActivity
Application Support 및 Library 사용과 관련된 사항을 나열합니다.

도움말 관련 액티비티가 준비중입니다.

## 피드백 및 라이선스 (Feedback and License)
피드백은 아래의 메일주소로 문의 바랍니다.

<moblab14@gmail.com>

라이선스는 MIT License 를 준수합니다.

자세한 사항은 LICENSE.txt 를 참조해주십시오.


