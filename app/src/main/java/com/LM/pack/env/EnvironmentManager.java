package com.LM.pack.env;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import com.LM.pack.BuildConfig;
import com.LM.pack.model.EnvironmentState;
import com.LM.pack.util.CommonUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class EnvironmentManager {
    private static final String TAG = "EnvironmentManager";

    public static final String PREFS_NAME = "lm_pack_tool_state";
    private static final String KEY_JDK_REGISTRY = "installed_jdk_registry";
    private static final String KEY_NDK_REGISTRY = "installed_ndk_registry";
    private static final String KEY_JDK_NAME = "installed_jdk_name";
    private static final String KEY_JDK_DIR = "installed_jdk_dir";
    private static final String KEY_ANDROID_SDK_DIR = "android_sdk_dir";
    private static final String KEY_NDK_NAME = "installed_ndk_name";
    private static final String KEY_NDK_DIR = "installed_ndk_dir";
    private static final String KEY_SELECTED_JDK_INDEX = "selected_jdk_index";
    private static final String KEY_SELECTED_NDK_INDEX = "selected_ndk_index";
    private static final String KEY_DOWNLOAD_ROUTE = "download_route";
    private static final String KEY_SDK_LICENSE_ACCEPTED = "sdk_license_accepted";

    public static final String DOWNLOAD_ROUTE_CHINA = "china";
    public static final String DOWNLOAD_ROUTE_GLOBAL = "global";

    public static final int DEFAULT_JDK_INDEX = 4;
    public static final int DEFAULT_NDK_INDEX = 4;
    public static final int EMBEDDED_JDK_INDEX = DEFAULT_JDK_INDEX;
    public static final int EMBEDDED_NDK_INDEX = DEFAULT_NDK_INDEX;
    public static final String DEFAULT_DOWNLOAD_ROUTE = DOWNLOAD_ROUTE_CHINA;

    public static final String SDK_DISPLAY_NAME = "Android SDK Command-line Tools";
    public static final String DEFAULT_GRADLE_VERSION = "8.7";
    public static final String REPOSITORY_RAW_BASE = BuildConfig.REPOSITORY_RAW_BASE;
    public static final String REPOSITORY_RAW_MIRROR_BASE = BuildConfig.REPOSITORY_RAW_MIRROR_BASE;
    public static final String DEFAULT_GRADLE_DISTRIBUTION_SHA256 = "544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d";
    public static final String DEFAULT_GRADLE_WRAPPER_SHA256 = "cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8";

    private static final LinkedHashMap<String, String> GRADLE_DISTRIBUTION_SHA256 = new LinkedHashMap<String, String>();
    private static final LinkedHashMap<String, String> GRADLE_WRAPPER_SHA256 = new LinkedHashMap<String, String>();

    static {
        GRADLE_DISTRIBUTION_SHA256.put("9.6.1", "9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14");
        GRADLE_DISTRIBUTION_SHA256.put("9.6.0", "bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01");
        GRADLE_DISTRIBUTION_SHA256.put("9.5.1", "bafc141b619ad6350fd975fc903156dd5c151998cc8b058e8c1044ab5f7b031f");
        GRADLE_DISTRIBUTION_SHA256.put("8.14.5", "6f74b601422d6d6fc4e1f9a1ab6522f642c2fdcbc15ae33ebd30ba3d7198e854");
        GRADLE_DISTRIBUTION_SHA256.put("9.5.0", "553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746");
        GRADLE_DISTRIBUTION_SHA256.put("9.4.1", "2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb");
        GRADLE_DISTRIBUTION_SHA256.put("9.4.0", "60ea723356d81263e8002fec0fcf9e2b0eee0c0850c7a3d7ab0a63f2ccc601f3");
        GRADLE_DISTRIBUTION_SHA256.put("9.3.1", "b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06");
        GRADLE_DISTRIBUTION_SHA256.put("8.14.4", "f1771298a70f6db5a29daf62378c4e18a17fc33c9ba6b14362e0cdf40610380d");
        GRADLE_DISTRIBUTION_SHA256.put("9.3.0", "0d585f69da091fc5b2beced877feab55a3064d43b8a1d46aeb07996b0915e0e0");
        GRADLE_DISTRIBUTION_SHA256.put("9.2.1", "72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f");
        GRADLE_DISTRIBUTION_SHA256.put("9.2.0", "df67a32e86e3276d011735facb1535f64d0d88df84fa87521e90becc2d735444");
        GRADLE_DISTRIBUTION_SHA256.put("9.1.0", "a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806");
        GRADLE_DISTRIBUTION_SHA256.put("9.0.0", "8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b");
        GRADLE_DISTRIBUTION_SHA256.put("8.14.3", "bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531");
        GRADLE_DISTRIBUTION_SHA256.put("7.6.6", "673d9776f303bc7048fc3329d232d6ebf1051b07893bd9d11616fad9a8673be0");
        GRADLE_DISTRIBUTION_SHA256.put("8.14.2", "7197a12f450794931532469d4ff21a59ea2c1cd59a3ec3f89c035c3c420a6999");
        GRADLE_DISTRIBUTION_SHA256.put("7.6.5", "b812fec0edb7d27e0ae35955887bb2954536fa3e44edaf481150da058e154d9a");
        GRADLE_DISTRIBUTION_SHA256.put("8.14.1", "845952a9d6afa783db70bb3b0effaae45ae5542ca2bb7929619e8af49cb634cf");
        GRADLE_DISTRIBUTION_SHA256.put("8.14", "61ad310d3c7d3e5da131b76bbf22b5a4c0786e9d892dae8c1658d4b484de3caa");
        GRADLE_DISTRIBUTION_SHA256.put("8.13", "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78");
        GRADLE_DISTRIBUTION_SHA256.put("8.12.1", "8d97a97984f6cbd2b85fe4c60a743440a347544bf18818048e611f5288d46c94");
        GRADLE_DISTRIBUTION_SHA256.put("8.12", "7a00d51fb93147819aab76024feece20b6b84e420694101f276be952e08bef03");
        GRADLE_DISTRIBUTION_SHA256.put("8.11.1", "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6");
        GRADLE_DISTRIBUTION_SHA256.put("8.11", "57dafb5c2622c6cc08b993c85b7c06956a2f53536432a30ead46166dbca0f1e9");
        GRADLE_DISTRIBUTION_SHA256.put("8.10.2", "31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26");
        GRADLE_DISTRIBUTION_SHA256.put("8.10.1", "1541fa36599e12857140465f3c91a97409b4512501c26f9631fb113e392c5bd1");
        GRADLE_DISTRIBUTION_SHA256.put("8.10", "5b9c5eb3f9fc2c94abaea57d90bd78747ca117ddbbf96c859d3741181a12bf2a");
        GRADLE_DISTRIBUTION_SHA256.put("8.9", "d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab");
        GRADLE_DISTRIBUTION_SHA256.put("8.8", "a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612");
        GRADLE_DISTRIBUTION_SHA256.put("8.7", DEFAULT_GRADLE_DISTRIBUTION_SHA256);
        GRADLE_DISTRIBUTION_SHA256.put("7.6.4", "bed1da33cca0f557ab13691c77f38bb67388119e4794d113e051039b80af9bb1");
        GRADLE_DISTRIBUTION_SHA256.put("8.6", "9631d53cf3e74bfa726893aee1f8994fee4e060c401335946dba2156f440f24c");
        GRADLE_DISTRIBUTION_SHA256.put("8.5", "9d926787066a081739e8200858338b4a69e837c3a821a33aca9db09dd4a41026");
        GRADLE_DISTRIBUTION_SHA256.put("8.4", "3e1af3ae886920c3ac87f7a91f816c0c7c436f276a6eefdb3da152100fef72ae");
        GRADLE_DISTRIBUTION_SHA256.put("7.6.3", "740c2e472ee4326c33bf75a5c9f5cd1e69ecf3f9b580f6e236c86d1f3d98cfac");
        GRADLE_DISTRIBUTION_SHA256.put("8.3", "591855b517fc635b9e04de1d05d5e76ada3f89f5fc76f87978d1b245b4f69225");
        GRADLE_DISTRIBUTION_SHA256.put("8.2.1", "03ec176d388f2aa99defcadc3ac6adf8dd2bce5145a129659537c0874dea5ad1");
        GRADLE_DISTRIBUTION_SHA256.put("8.2", "38f66cd6eef217b4c35855bb11ea4e9fbc53594ccccb5fb82dfd317ef8c2c5a3");
        GRADLE_DISTRIBUTION_SHA256.put("7.6.2", "a01b6587e15fe7ed120a0ee299c25982a1eee045abd6a9dd5e216b2f628ef9ac");
        GRADLE_DISTRIBUTION_SHA256.put("8.1.1", "e111cb9948407e26351227dabce49822fb88c37ee72f1d1582a69c68af2e702f");
        GRADLE_DISTRIBUTION_SHA256.put("8.1", "a62c5f99585dd9e1f95dab7b9415a0e698fa9dd1e6c38537faa81ac078f4d23e");
        GRADLE_DISTRIBUTION_SHA256.put("8.0.2", "ff7bf6a86f09b9b2c40bb8f48b25fc19cf2b2664fd1d220cd7ab833ec758d0d7");
        GRADLE_DISTRIBUTION_SHA256.put("7.6.1", "6147605a23b4eff6c334927a86ff3508cb5d6722cd624c97ded4c2e8640f1f87");
        GRADLE_DISTRIBUTION_SHA256.put("6.9.4", "3e240228538de9f18772a574e99a0ba959e83d6ef351014381acd9631781389a");
        GRADLE_DISTRIBUTION_SHA256.put("8.0.1", "1b6b558be93f29438d3df94b7dfee02e794b94d9aca4611a92cdb79b6b88e909");
        GRADLE_DISTRIBUTION_SHA256.put("8.0", "4159b938ec734a8388ce03f52aa8f3c7ed0d31f5438622545de4f83a89b79788");
        GRADLE_DISTRIBUTION_SHA256.put("7.6", "7ba68c54029790ab444b39d7e293d3236b2632631fb5f2e012bb28b4ff669e4b");
        GRADLE_DISTRIBUTION_SHA256.put("6.9.3", "dcf350b8ae1aa192fc299aed6efc77b43825d4fedb224c94118ae7faf5fb035d");
        GRADLE_DISTRIBUTION_SHA256.put("6.9.2", "8b356fd8702d5ffa2e066ed0be45a023a779bba4dd1a68fd11bc2a6bdc981e8f");
        GRADLE_DISTRIBUTION_SHA256.put("6.9.1", "8c12154228a502b784f451179846e518733cf856efc7d45b2e6691012977b2fe");
        GRADLE_DISTRIBUTION_SHA256.put("6.9", "765442b8069c6bee2ea70713861c027587591c6b1df2c857a23361512560894e");
        GRADLE_WRAPPER_SHA256.put("9.6.1", "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7");
        GRADLE_WRAPPER_SHA256.put("9.6.0", "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7");
        GRADLE_WRAPPER_SHA256.put("9.5.1", "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7");
        GRADLE_WRAPPER_SHA256.put("8.14.5", "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172");
        GRADLE_WRAPPER_SHA256.put("9.5.0", "497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7");
        GRADLE_WRAPPER_SHA256.put("9.4.1", "55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c");
        GRADLE_WRAPPER_SHA256.put("9.4.0", "55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c");
        GRADLE_WRAPPER_SHA256.put("9.3.1", "b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13");
        GRADLE_WRAPPER_SHA256.put("8.14.4", "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172");
        GRADLE_WRAPPER_SHA256.put("9.3.0", "b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13");
        GRADLE_WRAPPER_SHA256.put("9.2.1", "423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9");
        GRADLE_WRAPPER_SHA256.put("9.2.0", "423cb469ccc0ecc31f0e4e1c309976198ccb734cdcbb7029d4bda0f18f57e8d9");
        GRADLE_WRAPPER_SHA256.put("9.1.0", "76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3");
        GRADLE_WRAPPER_SHA256.put("9.0.0", "76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3");
        GRADLE_WRAPPER_SHA256.put("8.14.3", "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172");
        GRADLE_WRAPPER_SHA256.put("7.6.6", "14dfa961b6704bb3decdea06502781edaa796a82e6da41cd2e1962b14fbe21a3");
        GRADLE_WRAPPER_SHA256.put("8.14.2", "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172");
        GRADLE_WRAPPER_SHA256.put("7.6.5", "14dfa961b6704bb3decdea06502781edaa796a82e6da41cd2e1962b14fbe21a3");
        GRADLE_WRAPPER_SHA256.put("8.14.1", "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172");
        GRADLE_WRAPPER_SHA256.put("8.14", "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172");
        GRADLE_WRAPPER_SHA256.put("8.13", "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f");
        GRADLE_WRAPPER_SHA256.put("8.12.1", "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046");
        GRADLE_WRAPPER_SHA256.put("8.12", "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046");
        GRADLE_WRAPPER_SHA256.put("8.11.1", "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046");
        GRADLE_WRAPPER_SHA256.put("8.11", "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046");
        GRADLE_WRAPPER_SHA256.put("8.10.2", "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046");
        GRADLE_WRAPPER_SHA256.put("8.10.1", "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046");
        GRADLE_WRAPPER_SHA256.put("8.10", "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046");
        GRADLE_WRAPPER_SHA256.put("8.9", "498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17");
        GRADLE_WRAPPER_SHA256.put("8.8", "cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8");
        GRADLE_WRAPPER_SHA256.put("8.7", DEFAULT_GRADLE_WRAPPER_SHA256);
        GRADLE_WRAPPER_SHA256.put("7.6.4", "14dfa961b6704bb3decdea06502781edaa796a82e6da41cd2e1962b14fbe21a3");
        GRADLE_WRAPPER_SHA256.put("8.6", "d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd");
        GRADLE_WRAPPER_SHA256.put("8.5", "d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd");
        GRADLE_WRAPPER_SHA256.put("8.4", "0336f591bc0ec9aa0c9988929b93ecc916b3c1d52aed202c7381db144aa0ef15");
        GRADLE_WRAPPER_SHA256.put("7.6.3", "14dfa961b6704bb3decdea06502781edaa796a82e6da41cd2e1962b14fbe21a3");
        GRADLE_WRAPPER_SHA256.put("8.3", "0336f591bc0ec9aa0c9988929b93ecc916b3c1d52aed202c7381db144aa0ef15");
        GRADLE_WRAPPER_SHA256.put("8.2.1", "a8451eeda314d0568b5340498b36edf147a8f0d692c5ff58082d477abe9146e4");
        GRADLE_WRAPPER_SHA256.put("8.2", "a8451eeda314d0568b5340498b36edf147a8f0d692c5ff58082d477abe9146e4");
        GRADLE_WRAPPER_SHA256.put("7.6.2", "14dfa961b6704bb3decdea06502781edaa796a82e6da41cd2e1962b14fbe21a3");
        GRADLE_WRAPPER_SHA256.put("8.1.1", "ed2c26eba7cfb93cc2b7785d05e534f07b5b48b5e7fc941921cd098628abca58");
        GRADLE_WRAPPER_SHA256.put("8.1", "ed2c26eba7cfb93cc2b7785d05e534f07b5b48b5e7fc941921cd098628abca58");
        GRADLE_WRAPPER_SHA256.put("8.0.2", "91941f522fbfd4431cf57e445fc3d5200c85f957bda2de5251353cf11174f4b5");
        GRADLE_WRAPPER_SHA256.put("7.6.1", "c5a643cf80162e665cc228f7b16f343fef868e47d3a4836f62e18b7e17ac018a");
        GRADLE_WRAPPER_SHA256.put("6.9.4", "e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637");
        GRADLE_WRAPPER_SHA256.put("8.0.1", "91941f522fbfd4431cf57e445fc3d5200c85f957bda2de5251353cf11174f4b5");
        GRADLE_WRAPPER_SHA256.put("8.0", "91941f522fbfd4431cf57e445fc3d5200c85f957bda2de5251353cf11174f4b5");
        GRADLE_WRAPPER_SHA256.put("7.6", "c5a643cf80162e665cc228f7b16f343fef868e47d3a4836f62e18b7e17ac018a");
        GRADLE_WRAPPER_SHA256.put("6.9.3", "e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637");
        GRADLE_WRAPPER_SHA256.put("6.9.2", "e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637");
        GRADLE_WRAPPER_SHA256.put("6.9.1", "e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637");
        GRADLE_WRAPPER_SHA256.put("6.9", "e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637");
    }

    public static final String[] JDK_NAMES = {
        "JDK 8 (长期支持版)",
        "JDK 11 (长期支持版)",
        "JDK 17 (长期支持版)",
        "JDK 20 (过渡版本)",
        "JDK 21 (当前推荐版)",
        "JDK 22 (过渡版本)",
        "JDK 23 (过渡版本)",
        "JDK 24 (过渡版本)",
        "JDK 25 (前沿版本)",
        "JDK 26 (实验版本)"
    };

    public static final String[] JDK_URLS = {
        "https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/11/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/20/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/22/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/23/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/24/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse",
        "https://api.adoptium.net/v3/binary/latest/26/ga/linux/x64/jdk/hotspot/normal/eclipse"
    };

    public static final String[][] JDK_FALLBACK_URLS = {
        {"https://api.adoptium.net/v3/binary/latest/8/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/11/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/20/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/22/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/23/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/24/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"},
        {"https://api.adoptium.net/v3/binary/latest/26/ga/linux/x64/jdk/hotspot/normal/eclipse"}
    };

    public static final String[] NDK_NAMES = {
        "NDK r23c (旧项目兼容版)",
        "NDK r24 (标准静态版)",
        "NDK r25c (中期版本)",
        "NDK r26d (较新过渡版)",
        "NDK r27c (稳定版，推荐)",
        "NDK r28c (较新稳定版)",
        "NDK r29 Beta 3 (测试版)"
    };

    public static final String[] NDK_URLS = {
        "https://dl.google.com/android/repository/android-ndk-r23c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r24-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r25c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r26d-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r27c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r28c-linux.zip",
        "https://dl.google.com/android/repository/android-ndk-r29-beta3-linux.zip"
    };

    public static final String[][] NDK_FALLBACK_URLS = {
        {
            "https://googledownloads.cn/android/repository/android-ndk-r23c-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r23c-linux.zip"
        },
        {},
        {
            "https://googledownloads.cn/android/repository/android-ndk-r25c-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r25c-linux.zip"
        },
        {
            "https://googledownloads.cn/android/repository/android-ndk-r26d-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r26d-linux.zip"
        },
        {
            "https://googledownloads.cn/android/repository/android-ndk-r27c-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r27c-linux.zip"
        },
        {
            "https://googledownloads.cn/android/repository/android-ndk-r28c-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r28c-linux.zip"
        },
        {
            "https://googledownloads.cn/android/repository/android-ndk-r29-beta3-linux.zip",
            "https://redirector.gvt1.com/edgedl/android/repository/android-ndk-r29-beta3-linux.zip"
        }
    };

    public static final String SDK_OFFICIAL_URL = "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip";
    public static final String SDK_CHINA_URL = "https://googledownloads.cn/android/repository/commandlinetools-linux-13114758_latest.zip";

    public static final String[] SDK_FALLBACK_URLS = {
        "https://dl.google.com/android/repository/commandlinetools-linux-latest.zip",
        "https://googledownloads.cn/android/repository/commandlinetools-linux-latest.zip",
        "https://redirector.gvt1.com/edgedl/android/repository/commandlinetools-linux-13114758_latest.zip"
    };

    public static final String PLATFORM_TOOLS_OFFICIAL_URL = "https://dl.google.com/android/repository/platform-tools-latest-linux.zip";
    public static final String PLATFORM_TOOLS_CHINA_URL = "https://googledownloads.cn/android/repository/platform-tools-latest-linux.zip";

    public static final int[] ANDROID_API_LEVELS = {27, 28, 29, 30, 31, 32, 33, 34, 35, 36};
    public static final String[] ANDROID_VERSION_NAMES = {
        "Android 8.1", "Android 9", "Android 10", "Android 11", "Android 12",
        "Android 12L", "Android 13", "Android 14", "Android 15", "Android 16"
    };

    public static final String[][] ANDROID_PLATFORM_URLS = {
        {"platform-27_r03.zip", "https://dl.google.com/android/repository/platform-27_r03.zip"},
        {"platform-28_r06.zip", "https://dl.google.com/android/repository/platform-28_r06.zip"},
        {"platform-29_r05.zip", "https://dl.google.com/android/repository/platform-29_r05.zip"},
        {"platform-30_r03.zip", "https://dl.google.com/android/repository/platform-30_r03.zip"},
        {"platform-31_r01.zip", "https://dl.google.com/android/repository/platform-31_r01.zip"},
        {"platform-32_r01.zip", "https://dl.google.com/android/repository/platform-32_r01.zip"},
        {"platform-33-ext3_r03.zip", "https://dl.google.com/android/repository/platform-33-ext3_r03.zip"},
        {"platform-34-ext7_r03.zip", "https://dl.google.com/android/repository/platform-34-ext7_r03.zip"},
        {"platform-35_r02.zip", "https://dl.google.com/android/repository/platform-35_r02.zip"},
        {"platform-36_r02.zip", "https://dl.google.com/android/repository/platform-36_r02.zip"}
    };

    public static final String[][] ANDROID_BUILD_TOOLS = {
        {"27.0.3", "https://dl.google.com/android/repository/build-tools_r27.0.3-linux.zip"},
        {"28.0.3", "https://dl.google.com/android/repository/build-tools_r28.0.3-linux.zip"},
        {"29.0.3", "https://dl.google.com/android/repository/build-tools_r29.0.3-linux.zip"},
        {"30.0.3", "https://dl.google.com/android/repository/build-tools_r30.0.3-linux.zip"},
        {"31.0.0", "https://dl.google.com/android/repository/build-tools_r31-linux.zip"},
        {"32.0.0", "https://dl.google.com/android/repository/build-tools_r32-linux.zip"},
        {"33.0.3", "https://dl.google.com/android/repository/build-tools_r33.0.3-linux.zip"},
        {"34.0.0", "https://dl.google.com/android/repository/build-tools_r34-linux.zip"},
        {"35.0.1", "https://dl.google.com/android/repository/build-tools_r35.0.1_linux.zip"},
        {"36.0.0", "https://dl.google.com/android/repository/build-tools_r36_linux.zip"}
    };

    public static final String[] GRADLE_VERIFIED_DIRECT_URLS = {
        "https://services.gradle.org/distributions/gradle-8.7-bin.zip",
        "https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip",
        "https://downloads.gradle.org/distributions/gradle-8.7-bin.zip"
    };

    private final SharedPreferences sharedPreferences;
    private final File baseDir;

    public EnvironmentManager(Context context, SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
        this.baseDir = resolveBaseDir(context);
    }

    private File resolveBaseDir(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (Environment.isExternalStorageManager()) {
                    return new File(Environment.getExternalStorageDirectory(), "LMBuildTools");
                }
            } catch (Throwable t) {
            }
        }
        File external = context.getExternalFilesDir(null);
        if (external == null) {
            external = context.getFilesDir();
        }
        return new File(external, "LMBuildTools");
    }

    public EnvironmentState loadState() {
        LinkedHashMap<String, String> jdkRegistry = readRegistry(KEY_JDK_REGISTRY);
        LinkedHashMap<String, String> ndkRegistry = readRegistry(KEY_NDK_REGISTRY);
        boolean migrated = false;

        String legacyJdkName = safeText(sharedPreferences.getString(KEY_JDK_NAME, ""));
        String legacyJdkDir = safeText(sharedPreferences.getString(KEY_JDK_DIR, ""));
        if (jdkRegistry.isEmpty() && legacyJdkName.length() > 0 && legacyJdkDir.length() > 0) {
            jdkRegistry.put(legacyJdkName, legacyJdkDir);
            migrated = true;
        }

        String legacyNdkName = safeText(sharedPreferences.getString(KEY_NDK_NAME, ""));
        String legacyNdkDir = safeText(sharedPreferences.getString(KEY_NDK_DIR, ""));
        if (ndkRegistry.isEmpty() && legacyNdkName.length() > 0 && legacyNdkDir.length() > 0) {
            ndkRegistry.put(legacyNdkName, legacyNdkDir);
            migrated = true;
        }

        if (migrated) {
            sharedPreferences.edit()
                .putString(KEY_JDK_REGISTRY, encodeRegistry(jdkRegistry))
                .putString(KEY_NDK_REGISTRY, encodeRegistry(ndkRegistry))
                .apply();
        }

        return new EnvironmentState(
            jdkRegistry,
            sharedPreferences.getString(KEY_ANDROID_SDK_DIR, ""),
            ndkRegistry
        );
    }

    public EnvironmentState saveInstalledJdk(String name, String dir) {
        LinkedHashMap<String, String> registry = readRegistry(KEY_JDK_REGISTRY);
        String cleanName = safeText(name);
        String cleanDir = safeText(dir);
        if (cleanName.length() > 0 && cleanDir.length() > 0) {
            registry.put(cleanName, cleanDir);
        }
        sharedPreferences.edit()
            .putString(KEY_JDK_REGISTRY, encodeRegistry(registry))
            .putString(KEY_JDK_NAME, cleanName)
            .putString(KEY_JDK_DIR, cleanDir)
            .apply();
        return loadState();
    }

    public EnvironmentState saveInstalledNdk(String name, String dir) {
        LinkedHashMap<String, String> registry = readRegistry(KEY_NDK_REGISTRY);
        String cleanName = safeText(name);
        String cleanDir = safeText(dir);
        if (cleanName.length() > 0 && cleanDir.length() > 0) {
            registry.put(cleanName, cleanDir);
        }
        sharedPreferences.edit()
            .putString(KEY_NDK_REGISTRY, encodeRegistry(registry))
            .putString(KEY_NDK_NAME, cleanName)
            .putString(KEY_NDK_DIR, cleanDir)
            .apply();
        return loadState();
    }

    public EnvironmentState saveAndroidSdkDir(String dir) {
        sharedPreferences.edit()
            .putString(KEY_ANDROID_SDK_DIR, dir == null ? "" : dir)
            .apply();
        return loadState();
    }

    public int loadSelectedJdkIndex() {
        int index = sharedPreferences.getInt(KEY_SELECTED_JDK_INDEX, DEFAULT_JDK_INDEX);
        if (index < 0 || index >= JDK_NAMES.length) {
            return DEFAULT_JDK_INDEX;
        }
        return index;
    }

    public int loadSelectedNdkIndex() {
        int index = sharedPreferences.getInt(KEY_SELECTED_NDK_INDEX, DEFAULT_NDK_INDEX);
        if (index < 0 || index >= NDK_NAMES.length) {
            return DEFAULT_NDK_INDEX;
        }
        return index;
    }

    public void saveSelectedJdkIndex(int index) {
        if (index < 0 || index >= JDK_NAMES.length) {
            return;
        }
        sharedPreferences.edit().putInt(KEY_SELECTED_JDK_INDEX, index).apply();
    }

    public void saveSelectedNdkIndex(int index) {
        if (index < 0 || index >= NDK_NAMES.length) {
            return;
        }
        sharedPreferences.edit().putInt(KEY_SELECTED_NDK_INDEX, index).apply();
    }

    public String loadDownloadRoute() {
        String route = safeText(sharedPreferences.getString(KEY_DOWNLOAD_ROUTE, DEFAULT_DOWNLOAD_ROUTE));
        if (DOWNLOAD_ROUTE_CHINA.equals(route) || DOWNLOAD_ROUTE_GLOBAL.equals(route)) {
            return route;
        }
        return DEFAULT_DOWNLOAD_ROUTE;
    }

    public void saveDownloadRoute(String route) {
        String normalized = normalizeDownloadRoute(route);
        sharedPreferences.edit().putString(KEY_DOWNLOAD_ROUTE, normalized).apply();
    }

    public boolean isSdkLicenseAccepted() {
        return sharedPreferences.getBoolean(KEY_SDK_LICENSE_ACCEPTED, false);
    }

    public void saveSdkLicenseAccepted(boolean accepted) {
        sharedPreferences.edit().putBoolean(KEY_SDK_LICENSE_ACCEPTED, accepted).apply();
    }

    public boolean isSelectedJdkInstalled(int selectedJdkIndex, EnvironmentState state) {
        return isExistingDirectory(getSelectedJdkDir(selectedJdkIndex, state));
    }

    public boolean isSelectedNdkInstalled(int selectedNdkIndex, EnvironmentState state) {
        return isExistingDirectory(getSelectedNdkDir(selectedNdkIndex, state));
    }

    public boolean isAndroidSdkRegistered(EnvironmentState state) {
        return state != null && isExistingDirectory(state.getAndroidSdkDir());
    }

    public boolean isExistingDirectory(String path) {
        if (path == null || path.length() == 0) {
            return false;
        }
        File file = new File(path);
        return file.exists() && file.isDirectory();
    }

    public String getSelectedJdkName(int selectedJdkIndex) {
        return JDK_NAMES[normalizeJdkIndex(selectedJdkIndex)];
    }

    public String getSelectedNdkName(int selectedNdkIndex) {
        return NDK_NAMES[normalizeNdkIndex(selectedNdkIndex)];
    }

    public String getSelectedJdkDir(int selectedJdkIndex, EnvironmentState state) {
        if (state == null) {
            return "";
        }
        return safeText(state.getInstalledJdkDir(getSelectedJdkName(selectedJdkIndex)));
    }

    public String getSelectedNdkDir(int selectedNdkIndex, EnvironmentState state) {
        if (state == null) {
            return "";
        }
        return safeText(state.getInstalledNdkDir(getSelectedNdkName(selectedNdkIndex)));
    }

    public String getJdkInstallDir(String jdkName) {
        return new File(new File(baseDir, "jdk"), sanitizeDirName(jdkName)).getAbsolutePath();
    }

    public String getNdkInstallDir(String ndkName) {
        return new File(new File(baseDir, "ndk"), sanitizeDirName(ndkName)).getAbsolutePath();
    }

    public String getProjectRootDir(String appName) {
        return new File(new File(baseDir, "projects"), sanitizeDirName(appName)).getAbsolutePath();
    }

    public String getManagedProjectRootDir() {
        return new File(baseDir, "projects").getAbsolutePath();
    }

    public String getImportedProjectRootDir() {
        return new File(new File(baseDir, "android"), "data").getAbsolutePath();
    }

    public String getImportTempDir() {
        return new File(baseDir, "import_temp").getAbsolutePath();
    }

    public String getPackageCacheDir() {
        return new File(baseDir, "packages").getAbsolutePath();
    }

    public String getGradlePackageArchivePath() {
        return getPackageCacheDir() + "/gradle/gradle-" + DEFAULT_GRADLE_VERSION + "-bin.zip";
    }

    public String getGradlePackageArchivePath(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        return getPackageCacheDir() + "/gradle/gradle-" + version + "-bin.zip";
    }

    public String getSdkPackageArchivePath() {
        return getPackageCacheDir() + "/sdk/commandlinetools-linux-latest.zip";
    }

    public String getEmbeddedSdkInstallDir() {
        return new File(baseDir, "sdk").getAbsolutePath();
    }

    public String getEmbeddedSdkCmdlineToolsDir() {
        return new File(new File(getEmbeddedSdkInstallDir(), "cmdline-tools"), "latest").getAbsolutePath();
    }

    public String getSdkManagerPath() {
        return new File(new File(getEmbeddedSdkCmdlineToolsDir(), "bin"), "sdkmanager").getAbsolutePath();
    }

    public String getGradleInstallDir() {
        return new File(baseDir, "gradle").getAbsolutePath();
    }

    public String getGradleUserHomeDir() {
        return new File(baseDir, "gradle-user-home").getAbsolutePath();
    }

    public String getBaseDir() {
        return baseDir.getAbsolutePath();
    }

    public File getDefaultBrowseRootDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (Environment.isExternalStorageManager()) {
                    return Environment.getExternalStorageDirectory();
                }
            } catch (Throwable t) {
            }
        }
        return baseDir;
    }

    public String getJdkPackageArchivePath(int index) {
        return getPackageCacheDir() + "/jdk/" + sanitizeDirName(JDK_NAMES[normalizeJdkIndex(index)]) + ".tar.gz";
    }

    public String getNdkPackageArchivePath(int index) {
        return getPackageCacheDir() + "/ndk/" + sanitizeDirName(NDK_NAMES[normalizeNdkIndex(index)]) + ".zip";
    }

    public int recommendJdkIndex(File projectDir) {
        String gradleVersion = findGradleVersion(projectDir);
        String agpVersion = findAgpVersion(projectDir);
        String sourceCompatibility = findSourceCompatibility(projectDir);
        int gradleMajor = majorOfVersion(gradleVersion);
        int gradleMinor = minorOfVersion(gradleVersion);
        int agpMajor = majorOfVersion(agpVersion);
        int agpMinor = minorOfVersion(agpVersion);
        int javaLevel = parseJavaLevel(sourceCompatibility);

        if (javaLevel >= 26 || gradleMajor >= 10 || agpMajor >= 10) {
            return 9;
        }
        if (javaLevel >= 25 || gradleMajor >= 9 || agpMajor >= 9) {
            return 8;
        }
        if (javaLevel >= 24 || (gradleMajor == 8 && gradleMinor >= 12) || agpMajor >= 8) {
            return 7;
        }
        if (javaLevel >= 21 || gradleMajor >= 8 || agpMajor >= 8) {
            return 4;
        }
        if (javaLevel >= 20) {
            return 3;
        }
        if (javaLevel >= 17 || gradleMajor >= 7 || agpMajor >= 7) {
            return 2;
        }
        if (javaLevel >= 11 || gradleMajor >= 5 || agpMajor >= 4) {
            return 1;
        }
        if (gradleMajor <= 0 && agpMajor <= 0 && javaLevel <= 0) {
            return 4;
        }
        return 0;
    }

    public int recommendNdkIndex(File projectDir) {
        String ndkVersion = findNdkVersion(projectDir);
        if (ndkVersion.startsWith("29") || ndkVersion.contains("r29")) {
            return 6;
        }
        if (ndkVersion.startsWith("28") || ndkVersion.contains("r28")) {
            return 5;
        }
        if (ndkVersion.startsWith("27") || ndkVersion.contains("r27")) {
            return 4;
        }
        if (ndkVersion.startsWith("26") || ndkVersion.contains("r26")) {
            return 3;
        }
        if (ndkVersion.startsWith("25") || ndkVersion.contains("r25")) {
            return 2;
        }
        if (ndkVersion.startsWith("23") || ndkVersion.contains("r23")) {
            return 0;
        }
        return 4;
    }

    public String recommendGradleVersion(File projectDir) {
        String version = findGradleVersion(projectDir);
        return version.length() == 0 ? DEFAULT_GRADLE_VERSION : version;
    }

    public String recommendBuildToolsVersion(File projectDir) {
        String value = extractFromGradle(projectDir, "buildToolsVersion\\s*(?:=\\s*)?[\"']([^\"']+)[\"']");
        if (value.length() > 0) {
            return value;
        }
        String compileSdk = extractFromGradle(projectDir, "compileSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)");
        if (compileSdk.length() > 0) {
            return getDefaultBuildToolsForApi(parseIntSafe(compileSdk));
        }
        return "36.0.0";
    }

    public String getDefaultBuildToolsForApi(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                return ANDROID_BUILD_TOOLS[i][0];
            }
        }
        return apiLevel + ".0.0";
    }

    public String getAndroidPlatformUrl(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                return ANDROID_PLATFORM_URLS[i][1];
            }
        }
        return "";
    }

    public String getAndroidBuildToolsUrl(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                return ANDROID_BUILD_TOOLS[i][1];
            }
        }
        return "";
    }

    public String getAndroidPlatformFileName(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                return ANDROID_PLATFORM_URLS[i][0];
            }
        }
        return "platform-" + apiLevel + ".zip";
    }

    public String getAndroidBuildToolsFileName(int apiLevel) {
        for (int i = 0; i < ANDROID_API_LEVELS.length; i++) {
            if (ANDROID_API_LEVELS[i] == apiLevel) {
                String[] parts = ANDROID_BUILD_TOOLS[i][1].split("/");
                return parts[parts.length - 1];
            }
        }
        return "build-tools-" + apiLevel + ".zip";
    }

    public String getPlatformToolsDownloadUrl() {
        if (isChinaDownloadRoute()) {
            return PLATFORM_TOOLS_CHINA_URL;
        }
        return PLATFORM_TOOLS_OFFICIAL_URL;
    }

    public String recommendCompileSdk(File projectDir) {
        String value = extractFromGradle(projectDir, "compileSdk(?:Version)?\\s*(?:=\\s*)?(\\d+)");
        return value.length() == 0 ? "36" : value;
    }

    public String getRecommendedJdkName(File projectDir) {
        return getSelectedJdkName(recommendJdkIndex(projectDir));
    }

    public String getRecommendedNdkName(File projectDir) {
        return getSelectedNdkName(recommendNdkIndex(projectDir));
    }

    public String[] getSdkDownloadCandidates() {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (isChinaDownloadRoute()) {
            values.add(SDK_CHINA_URL);
            values.add(SDK_OFFICIAL_URL);
        } else {
            values.add(SDK_OFFICIAL_URL);
            values.add(SDK_CHINA_URL);
        }
        appendUrls(values, SDK_FALLBACK_URLS);
        return values.toArray(new String[0]);
    }

    public String[] getJdkDownloadCandidates(int index) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        int safeIndex = normalizeJdkIndex(index);
        values.add(JDK_URLS[safeIndex]);
        appendUrls(values, JDK_FALLBACK_URLS[safeIndex]);
        return values.toArray(new String[0]);
    }

    public String[] getNdkDownloadCandidates(int index) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        int safeIndex = normalizeNdkIndex(index);
        if (isChinaDownloadRoute()) {
            appendUrls(values, NDK_FALLBACK_URLS[safeIndex]);
            values.add(NDK_URLS[safeIndex]);
        } else {
            values.add(NDK_URLS[safeIndex]);
            appendUrls(values, NDK_FALLBACK_URLS[safeIndex]);
        }
        return values.toArray(new String[0]);
    }

    public String[] getGradleDownloadCandidates(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        String official = "https://services.gradle.org/distributions/gradle-" + version + "-bin.zip";
        String mirror = "https://mirrors.cloud.tencent.com/gradle/gradle-" + version + "-bin.zip";
        String backup = "https://downloads.gradle.org/distributions/gradle-" + version + "-bin.zip";
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (isChinaDownloadRoute()) {
            values.add(mirror);
            values.add(official);
            values.add(backup);
        } else {
            values.add(official);
            values.add(mirror);
            values.add(backup);
        }
        return values.toArray(new String[0]);
    }

    public boolean isChinaDownloadRoute() {
        return DOWNLOAD_ROUTE_CHINA.equals(loadDownloadRoute());
    }

    public String getDownloadRouteDisplayName() {
        return isChinaDownloadRoute() ? "国内路线（镜像优先）" : "国外路线（官方优先）";
    }

    public String getDownloadRegionLabel() {
        return getDownloadRouteDisplayName();
    }

    public String buildToolchainRecommendationSummary(File projectDir) {
        String compileSdk = recommendCompileSdk(projectDir);
        String buildTools = recommendBuildToolsVersion(projectDir);
        String gradleVersion = recommendGradleVersion(projectDir);
        return "推荐环境："
            + getRecommendedJdkName(projectDir)
            + " / "
            + getRecommendedNdkName(projectDir)
            + " / SDK android-" + compileSdk
            + " / Build-Tools " + buildTools
            + " / Gradle " + gradleVersion
            + " / " + getDownloadRegionLabel();
    }

    public String getRepositoryWrapperJarUrl() {
        String[] candidates = getRepositoryWrapperJarCandidates();
        return candidates.length == 0 ? "" : candidates[0];
    }

    public String getRepositoryWrapperPropertiesUrl() {
        String[] candidates = getRepositoryWrapperPropertiesCandidates();
        return candidates.length == 0 ? "" : candidates[0];
    }

    public String[] getRepositoryWrapperJarCandidates() {
        return buildRepositoryRawCandidates("/gradle/wrapper/gradle-wrapper.jar");
    }

    public String[] getRepositoryWrapperPropertiesCandidates() {
        return buildRepositoryRawCandidates("/gradle/wrapper/gradle-wrapper.properties");
    }

    public String getOfficialWrapperJarUrl(String gradleVersion) {
        return "https://raw.githubusercontent.com/gradle/gradle/v" + normalizeGradleTag(gradleVersion) + "/gradle/wrapper/gradle-wrapper.jar";
    }

    public String getOfficialWrapperPropertiesUrl(String gradleVersion) {
        return "https://raw.githubusercontent.com/gradle/gradle/v" + normalizeGradleTag(gradleVersion) + "/gradle/wrapper/gradle-wrapper.properties";
    }

    public String getGradleDistributionSha256(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        String value = GRADLE_DISTRIBUTION_SHA256.get(version);
        return value == null ? "" : value;
    }

    public String getGradleWrapperSha256(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        String value = GRADLE_WRAPPER_SHA256.get(version);
        return value == null ? "" : value;
    }

    public String buildWrapperPropertiesContent(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        String distributionSha256 = getGradleDistributionSha256(version);
        String distributionUrl = getPreferredGradleDistributionUrl(version);
        return "distributionBase=GRADLE_USER_HOME\n"
            + "distributionPath=wrapper/dists\n"
            + "distributionUrl=" + escapePropertiesUrl(distributionUrl) + "\n"
            + (distributionSha256.length() == 0 ? "" : "distributionSha256Sum=" + distributionSha256 + "\n")
            + "networkTimeout=10000\n"
            + "validateDistributionUrl=true\n"
            + "zipStoreBase=GRADLE_USER_HOME\n"
            + "zipStorePath=wrapper/dists\n";
    }

    public String getPreferredGradleDistributionUrl(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        if (isChinaDownloadRoute()) {
            return "https://mirrors.cloud.tencent.com/gradle/gradle-" + version + "-bin.zip";
        }
        return "https://services.gradle.org/distributions/gradle-" + version + "-bin.zip";
    }

    private String sanitizeDirName(String name) {
        return name.replace(" ", "_")
            .replace("(", "")
            .replace(")", "")
            .replace("，", "_")
            .replace("/", "_");
    }

    private String normalizeDownloadRoute(String route) {
        String value = safeText(route).toLowerCase(Locale.US);
        if (DOWNLOAD_ROUTE_GLOBAL.equals(value)) {
            return DOWNLOAD_ROUTE_GLOBAL;
        }
        return DOWNLOAD_ROUTE_CHINA;
    }

    private int parseIntSafe(String value) {
        return CommonUtils.parseIntSafe(value);
    }

    private String escapePropertiesUrl(String url) {
        return safeText(url).replace(":", "\\:");
    }

    private int normalizeJdkIndex(int index) {
        if (index < 0 || index >= JDK_NAMES.length) {
            return DEFAULT_JDK_INDEX;
        }
        return index;
    }

    private int normalizeNdkIndex(int index) {
        if (index < 0 || index >= NDK_NAMES.length) {
            return DEFAULT_NDK_INDEX;
        }
        return index;
    }

    private LinkedHashMap<String, String> readRegistry(String key) {
        LinkedHashMap<String, String> registry = new LinkedHashMap<String, String>();
        String raw = sharedPreferences.getString(key, "");
        if (raw == null || raw.trim().length() == 0) {
            return registry;
        }
        try {
            JSONObject jsonObject = new JSONObject(raw);
            Iterator<String> iterator = jsonObject.keys();
            while (iterator.hasNext()) {
                String name = safeText(iterator.next());
                String dir = safeText(jsonObject.optString(name, ""));
                if (name.length() == 0 || dir.length() == 0) {
                    continue;
                }
                registry.put(name, dir);
            }
        } catch (Exception e) {
            Log.w(TAG, "解析本地工具链注册表失败", e);
        }
        return registry;
    }

    private String encodeRegistry(Map<String, String> registry) {
        JSONObject jsonObject = new JSONObject();
        if (registry != null) {
            for (Map.Entry<String, String> entry : registry.entrySet()) {
                String name = safeText(entry.getKey());
                String dir = safeText(entry.getValue());
                if (name.length() == 0 || dir.length() == 0) {
                    continue;
                }
                try {
                    jsonObject.put(name, dir);
                } catch (Exception e) {
                    Log.w(TAG, "写入本地工具链注册表项失败", e);
                }
            }
        }
        return jsonObject.toString();
    }

    private void appendUrls(LinkedHashSet<String> values, String[] candidates) {
        if (candidates == null) {
            return;
        }
        for (int i = 0; i < candidates.length; i++) {
            String value = safeText(candidates[i]);
            if (value.length() > 0) {
                values.add(value);
            }
        }
    }

    private String[] buildRepositoryRawCandidates(String suffix) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        String normalizedSuffix = suffix.startsWith("/") ? suffix : "/" + suffix;
        if (isChinaDownloadRoute()) {
            addRepositoryRawUrl(values, REPOSITORY_RAW_MIRROR_BASE, normalizedSuffix);
            addRepositoryRawUrl(values, REPOSITORY_RAW_BASE, normalizedSuffix);
        } else {
            addRepositoryRawUrl(values, REPOSITORY_RAW_BASE, normalizedSuffix);
            addRepositoryRawUrl(values, REPOSITORY_RAW_MIRROR_BASE, normalizedSuffix);
        }
        return values.toArray(new String[0]);
    }

    private void addRepositoryRawUrl(LinkedHashSet<String> values, String baseUrl, String suffix) {
        String normalizedBase = safeText(baseUrl);
        if (normalizedBase.length() == 0) {
            return;
        }
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        values.add(normalizedBase + suffix);
    }

    private String findGradleVersion(File projectDir) {
        File wrapperProperties = new File(projectDir, "gradle/wrapper/gradle-wrapper.properties");
        if (!wrapperProperties.exists()) {
            return "";
        }
        try {
            String content = readText(wrapperProperties);
            Matcher matcher = Pattern.compile("gradle-([0-9][0-9A-Za-z.\\-]*)-(?:bin|all)\\.zip").matcher(content);
            if (matcher.find()) {
                return safeText(matcher.group(1));
            }
        } catch (Exception e) {
            Log.w(TAG, "读取 Gradle Wrapper 版本失败", e);
        }
        return "";
    }

    private String findAgpVersion(File projectDir) {
        String fromRoot = extractFromFile(new File(projectDir, "build.gradle"), "com\\.android\\.tools\\.build:gradle:([0-9][0-9A-Za-z.\\-]*)");
        if (fromRoot.length() > 0) {
            return fromRoot;
        }
        fromRoot = extractFromFile(new File(projectDir, "build.gradle.kts"), "com\\.android\\.tools\\.build:gradle:([0-9][0-9A-Za-z.\\-]*)");
        if (fromRoot.length() > 0) {
            return fromRoot;
        }
        String pluginVersion = extractFromFile(new File(projectDir, "settings.gradle"), "id\\s*[\"']com\\.android\\.(?:application|library)[\"']\\s*version\\s*[\"']([0-9][0-9A-Za-z.\\-]*)[\"']");
        if (pluginVersion.length() > 0) {
            return pluginVersion;
        }
        return extractFromFile(new File(projectDir, "settings.gradle.kts"), "id\\s*\\([\"']com\\.android\\.(?:application|library)[\"']\\)\\s*version\\s*[\"']([0-9][0-9A-Za-z.\\-]*)[\"']");
    }

    private String findSourceCompatibility(File projectDir) {
        String value = extractFromGradle(projectDir, "sourceCompatibility\\s*(?:=\\s*)?JavaVersion\\.VERSION_(\\d+_\\d+|\\d+)");
        if (value.length() > 0) {
            return value;
        }
        return extractFromGradle(projectDir, "sourceCompatibility\\s*(?:=\\s*)?[\"']?([0-9][0-9A-Za-z._-]*)[\"']?");
    }

    private String findNdkVersion(File projectDir) {
        return extractFromGradle(projectDir, "ndkVersion\\s*(?:=\\s*)?[\"']([^\"']+)[\"']");
    }

    private String extractFromGradle(File projectDir, String regex) {
        String value = extractFromFile(new File(projectDir, "app/build.gradle"), regex);
        if (value.length() > 0) {
            return value;
        }
        return extractFromFile(new File(projectDir, "app/build.gradle.kts"), regex);
    }

    private String extractFromFile(File file, String regex) {
        if (file == null || !file.exists()) {
            return "";
        }
        try {
            Matcher matcher = Pattern.compile(regex).matcher(readText(file));
            if (matcher.find()) {
                return safeText(matcher.group(1));
            }
        } catch (Exception e) {
            Log.w(TAG, "从文件提取配置项失败: " + file.getAbsolutePath(), e);
        }
        return "";
    }

    private int parseJavaLevel(String value) {
        String clean = safeText(value).replace("VERSION_", "").replace("_", ".");
        if ("1.8".equals(clean)) {
            return 8;
        }
        int major = majorOfVersion(clean);
        return major <= 0 ? 0 : major;
    }

    private int majorOfVersion(String value) {
        String clean = safeText(value);
        if (clean.length() == 0) {
            return 0;
        }
        Matcher matcher = Pattern.compile("^(\\d+)").matcher(clean);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception e) {
            return 0;
        }
    }

    private int minorOfVersion(String value) {
        String clean = safeText(value);
        Matcher matcher = Pattern.compile("^\\d+\\.(\\d+)").matcher(clean);
        if (!matcher.find()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception e) {
            return 0;
        }
    }

    private String normalizeGradleTag(String gradleVersion) {
        String version = safeText(gradleVersion);
        if (version.length() == 0) {
            version = DEFAULT_GRADLE_VERSION;
        }
        if (Pattern.compile("^\\d+\\.\\d+$").matcher(version).find()) {
            return version + ".0";
        }
        return version;
    }

    private String readText(File file) throws Exception {
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            return new String(outputStream.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }

    private String safeText(String value) {
        return CommonUtils.safeText(value);
    }

    public static class JdkVersion {
        public final String name;
        public final String url;
        public final String[] fallbackUrls;

        public JdkVersion(String name, String url, String[] fallbackUrls) {
            this.name = name;
            this.url = url;
            this.fallbackUrls = fallbackUrls;
        }
    }

    public static class NdkVersion {
        public final String name;
        public final String url;
        public final String[] fallbackUrls;

        public NdkVersion(String name, String url, String[] fallbackUrls) {
            this.name = name;
            this.url = url;
            this.fallbackUrls = fallbackUrls;
        }
    }

    public static final JdkVersion[] JDK_VERSIONS = buildJdkVersions();
    public static final NdkVersion[] NDK_VERSIONS = buildNdkVersions();

    private static JdkVersion[] buildJdkVersions() {
        return new JdkVersion[] {
            new JdkVersion(JDK_NAMES[0], JDK_URLS[0], JDK_FALLBACK_URLS[0]),
            new JdkVersion(JDK_NAMES[1], JDK_URLS[1], JDK_FALLBACK_URLS[1]),
            new JdkVersion(JDK_NAMES[2], JDK_URLS[2], JDK_FALLBACK_URLS[2]),
            new JdkVersion(JDK_NAMES[3], JDK_URLS[3], JDK_FALLBACK_URLS[3]),
            new JdkVersion(JDK_NAMES[4], JDK_URLS[4], JDK_FALLBACK_URLS[4]),
            new JdkVersion(JDK_NAMES[5], JDK_URLS[5], JDK_FALLBACK_URLS[5]),
            new JdkVersion(JDK_NAMES[6], JDK_URLS[6], JDK_FALLBACK_URLS[6]),
            new JdkVersion(JDK_NAMES[7], JDK_URLS[7], JDK_FALLBACK_URLS[7]),
            new JdkVersion(JDK_NAMES[8], JDK_URLS[8], JDK_FALLBACK_URLS[8]),
            new JdkVersion(JDK_NAMES[9], JDK_URLS[9], JDK_FALLBACK_URLS[9]),
        };
    }

    private static NdkVersion[] buildNdkVersions() {
        return new NdkVersion[] {
            new NdkVersion(NDK_NAMES[0], NDK_URLS[0], NDK_FALLBACK_URLS[0]),
            new NdkVersion(NDK_NAMES[1], NDK_URLS[1], NDK_FALLBACK_URLS[1]),
            new NdkVersion(NDK_NAMES[2], NDK_URLS[2], NDK_FALLBACK_URLS[2]),
            new NdkVersion(NDK_NAMES[3], NDK_URLS[3], NDK_FALLBACK_URLS[3]),
            new NdkVersion(NDK_NAMES[4], NDK_URLS[4], NDK_FALLBACK_URLS[4]),
            new NdkVersion(NDK_NAMES[5], NDK_URLS[5], NDK_FALLBACK_URLS[5]),
            new NdkVersion(NDK_NAMES[6], NDK_URLS[6], NDK_FALLBACK_URLS[6]),
        };
    }
}
