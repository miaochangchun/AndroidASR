package com.sinovoice.example;


import java.io.UnsupportedEncodingException;

import android.content.Context;
import android.util.Log;
import android.widget.TextView;

import com.sinovoice.hcicloudsdk.api.HciCloudSys;
import com.sinovoice.hcicloudsdk.api.asr.HciCloudAsr;
import com.sinovoice.hcicloudsdk.common.HciErrorCode;
import com.sinovoice.hcicloudsdk.common.Session;
import com.sinovoice.hcicloudsdk.common.asr.AsrConfig;
import com.sinovoice.hcicloudsdk.common.asr.AsrGrammarId;
import com.sinovoice.hcicloudsdk.common.asr.AsrInitParam;
import com.sinovoice.hcicloudsdk.common.asr.AsrRecogResult;


public class HciCloudFuncHelper extends HciCloudHelper{
    private static final String TAG = HciCloudFuncHelper.class.getSimpleName();

    /**
     * 非实时识别，是指把一整段音频进行上传，上传之后在开始识别并返回结果。
     * @param capkey	使用的capkey信息，使用云端的可以设置为asr.cloud.freetalk；使用本地的设置为asr.local.grammar.v4
     * @param recogConfig	语音识别的配置串，可以参考开发手册
     * @param audioFile	音频文件的路径，是从Assets目录下读取的
     */
	public static void Recog(String capkey,AsrConfig recogConfig,String audioFile) {
		ShowMessage("......Recog ......");

		// 载入语音数据文件
		byte[] voiceData = getAssetFileData(audioFile);
		if (null == voiceData) {
			ShowMessage("Open input voice file" + audioFile + " error!");
			return;
		}

		// 启动 ASR Session
		int errCode = -1;
		AsrConfig config = new AsrConfig();
		config.addParam(AsrConfig.SessionConfig.PARAM_KEY_CAP_KEY, capkey);
		config.addParam(AsrConfig.SessionConfig.PARAM_KEY_REALTIME, "no");
		String sSessionConfig = config.getStringConfig();
		Log.i(TAG, "hciAsrSessionStart config: " + sSessionConfig);
		Session nSessionId = new Session();
		errCode = HciCloudAsr.hciAsrSessionStart(sSessionConfig, nSessionId);
		if (HciErrorCode.HCI_ERR_NONE != errCode) {
			ShowMessage("hciAsrSessionStart error:" + HciCloudSys.hciGetErrorInfo(errCode));
			return;
		}
		ShowMessage("hciAsrSessionStart Success");

		// 识别
		AsrRecogResult asrResult = new AsrRecogResult();
		Log.i(TAG, "HciCloudAsr hciAsrRecog config：" + recogConfig + "\n");
		errCode = HciCloudAsr.hciAsrRecog(nSessionId, voiceData, recogConfig.getStringConfig(),
				null, asrResult);

		if (HciErrorCode.HCI_ERR_NONE == errCode) {
			Log.i(TAG, "HciCloudAsr hciAsrRecog Success");
			// 输出识别结果
			printAsrResult(asrResult);
		}
		else{
			ShowMessage("hciAsrRecog error:" + HciCloudSys.hciGetErrorInfo(errCode));
		}
		
		// 终止session
		HciCloudAsr.hciAsrSessionStop(nSessionId);
		ShowMessage("hciAsrSessionStop");
	}

	
	/**
	 * 实时识别，把音频进行分段上传，模拟实时识别。每3200字节上传一次，最后一次上传必须要明确出来，最后一次上传之后并开始把识别的结果返回
	 * @param capkey	使用的capkey信息，云端识别设置为asr.cloud.freetalk;本地识别设置为asr.local.grammar.v4
	 * @param recogConfig	语音识别的配置串，参考开发手册
	 * @param audioFile	音频文件所在路径，是Assets目录的下路径
	 */
	public static void RealtimeRecog(String capkey,AsrConfig recogConfig,String audioFile) {
		ShowMessage("......RealtimeRecog ......");

		// 载入语音数据文件
		byte[] voiceData = getAssetFileData(audioFile);
		if (null == voiceData) {
			ShowMessage("Open input voice file" + audioFile + " error!");
			return;
		}

		// 启动 ASR Session
		int errCode = -1;
		AsrConfig config = new AsrConfig();
		config.addParam(AsrConfig.SessionConfig.PARAM_KEY_CAP_KEY, capkey);
		config.addParam(AsrConfig.SessionConfig.PARAM_KEY_REALTIME, AsrConfig.VALUE_OF_YES);
		String sSessionConfig = config.getStringConfig();
		Log.i(TAG, "hciAsrSessionStart config: " + sSessionConfig);
		Session nSessionId = new Session();
		errCode = HciCloudAsr.hciAsrSessionStart(sSessionConfig, nSessionId);
		if (HciErrorCode.HCI_ERR_NONE != errCode) {
			ShowMessage("hciAsrSessionStart error:" + HciCloudSys.hciGetErrorInfo(errCode));
			return;
		}
		ShowMessage("hciAsrSessionStart Success");

		AsrRecogResult asrResult = new AsrRecogResult();
		int nPerLen = 3200;
		int nLen = 0;
		while (nLen + nPerLen < voiceData.length) {
			if (voiceData.length - nLen <= nPerLen) {
				nPerLen = voiceData.length - nLen;
			}

			byte[] subVoiceData = new byte[nPerLen];
			System.arraycopy(voiceData, nLen, subVoiceData, 0, nPerLen);
			// 调用识别方法,将音频数据不断的进行上传操作
			errCode = HciCloudAsr.hciAsrRecog(nSessionId, subVoiceData,
					recogConfig.getStringConfig(), null, asrResult);

			//如果返回值不是继续等待上传，需要判断是否到识别的结束位置，并跳出循环
			if (errCode != HciErrorCode.HCI_ERR_ASR_REALTIME_WAITING) {
				// 识别成功
				if (errCode == HciErrorCode.HCI_ERR_ASR_REALTIME_END) {
					// 检测到端点
					break;
				} else {
					break;
				}
			}
			// 否则是等待数据
			nLen += nPerLen;
		}

		// 若未检测到端点，但数据已经传入完毕，则需要告诉引擎数据输入完毕
		if (errCode == HciErrorCode.HCI_ERR_ASR_REALTIME_END
				|| errCode == HciErrorCode.HCI_ERR_ASR_REALTIME_WAITING) {
			//最后一次识别，并返回语音识别的结果，放到asrResult的对象中。
			errCode = HciCloudAsr.hciAsrRecog(nSessionId, null, recogConfig.getStringConfig(),
					null, asrResult);
		}
		//识别成功
		if (HciErrorCode.HCI_ERR_NONE == errCode) {
			Log.i(TAG, "HciCloudAsr hciAsrRecog Success");
			// 输出识别结果
			printAsrResult(asrResult);
		}
		else{
			ShowMessage("hciAsrRecog error:" + HciCloudSys.hciGetErrorInfo(errCode));
		}
		
		// 终止session
		HciCloudAsr.hciAsrSessionStop(nSessionId);
		ShowMessage("hciAsrSessionStop");
	}
	
	/**
	 * 输出ASR识别结果
	 * @param recogResult 识别结果
	 */
	private static void printAsrResult(AsrRecogResult recogResult) {
		if (recogResult.getRecogItemList().size() < 1) {
			ShowMessage("recognize result is null");
		}
		for (int i = 0; i < recogResult.getRecogItemList().size(); i++) {
			if (recogResult.getRecogItemList().get(i).getRecogResult() != null) {
				//识别结果
				String utf8 = recogResult.getRecogItemList().get(i).getRecogResult();
				//置信度，此数值对asr.local.grammar.v4有效，得分大于30时认为识别正确，得分小于30认为是杂音，不可行可以舍弃掉。
				//如果是asr.cloud.freetalk 此值可以不用关心。
				int score = recogResult.getRecogItemList().get(i).getScore();
				
				ShowMessage("result index:" + String.valueOf(i) + " result:" + utf8 + "\tScore:" + score);
			} else {
				ShowMessage("result index:" + String.valueOf(i) + " result: null");
			}
		}
	}
	
	/**
	 * 功能函数
	 * @param context	上下文对象
	 * @param capkey	设置的capkey信息，云端为asr.cloud.freetalk本地为asr.local.grammar.v4
	 * @param view	view对象
	 */
	public static void Func(Context context,String capkey,TextView view) {			

		setTextView(view);
		setContext(context);
		
		//初始化ASR
		//构造Asr初始化的帮助类的实例
        AsrInitParam asrInitParam = new AsrInitParam();
        // 获取App应用中的lib的路径,放置能力所需资源文件。如果使用/data/data/packagename/lib目录,需要添加android_so的标记
        //使用本地的capkey需要配置dataPath，云端的capkey可以不用配置。
        String dataPath = context.getFilesDir().getAbsolutePath()
                .replace("files", "lib");
        asrInitParam.addParam(AsrInitParam.PARAM_KEY_DATA_PATH, dataPath);
        //加载语音库以so的方式，需要把对应的音库资源拷贝到libs/armeabi目录下，并修改名字为libxxx.so的方式。
        //还可以按照none的方式加载，此时不需要对音库修改名称，直接拷贝到dataPath目录下即可，最好设置dataPath为sd卡目录。比如
        //String dataPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "sinovoice";
        asrInitParam.addParam(AsrInitParam.PARAM_KEY_FILE_FLAG, AsrInitParam.VALUE_OF_PARAM_FILE_FLAG_ANDROID_SO);
        asrInitParam.addParam(AsrInitParam.PARAM_KEY_INIT_CAP_KEYS, capkey);
        ShowMessage("HciAsrInit config :" + asrInitParam.getStringConfig());
        int errCode = HciCloudAsr.hciAsrInit(asrInitParam.getStringConfig());
        if (errCode != HciErrorCode.HCI_ERR_NONE) {
            ShowMessage("HciAsrInit error:" + HciCloudSys.hciGetErrorInfo(errCode));
            return;
        } else {
        	ShowMessage("HciAsrInit Success");
        }
        
        //使用grammar需要加载语法文件，语法为stock_10001.gram
        AsrGrammarId grammarId = new AsrGrammarId();
        if(capkey.equalsIgnoreCase("asr.local.grammar.v4")){
        	
        	AsrConfig loadConfig = new AsrConfig();
        	loadConfig.addParam(AsrConfig.GrammarConfig.PARAM_KEY_GRAMMAR_TYPE, AsrConfig.GrammarConfig.VALUE_OF_PARAM_GRAMMAR_TYPE_JSGF);
        	loadConfig.addParam(AsrConfig.GrammarConfig.PARAM_KEY_IS_FILE, AsrConfig.VALUE_OF_NO);
        	loadConfig.addParam(AsrConfig.SessionConfig.PARAM_KEY_CAP_KEY, capkey);
    		byte[] grammarData = getAssetFileData("stock_10001.gram");
    		String strGrammarData = null;
			try {
				strGrammarData = new String(grammarData, "utf-8");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	errCode = HciCloudAsr.hciAsrLoadGrammar(loadConfig.getStringConfig(), strGrammarData, grammarId);
        	if (errCode != HciErrorCode.HCI_ERR_NONE) {
                ShowMessage("hciAsrLoadGrammar error:" + HciCloudSys.hciGetErrorInfo(errCode));
                HciCloudAsr.hciAsrRelease();        
                return;
            } else {
            	ShowMessage("hciAsrLoadGrammar Success");
            }
        }
        
        AsrConfig recogConfig = new AsrConfig();
        if(grammarId.isValid()){
        	recogConfig.addParam(AsrConfig.GrammarConfig.PARAM_KEY_GRAMMAR_TYPE, AsrConfig.GrammarConfig.VALUE_OF_PARAM_GRAMMAR_TYPE_ID);
        	recogConfig.addParam(AsrConfig.GrammarConfig.PARAM_KEY_GRAMMAR_ID, grammarId.toString());
        }
        
//        //使用公有云能力asr.cloud.freetalk.music或asr.cloud.freetalk.poi时需要传相对应的domain参数
//        if (capkey.indexOf("asr.cloud.freetalk.music") != -1)
//        {
//        	recogConfig.addParam(AsrConfig.ResultConfig.PARAM_KEY_DOMAIN, "music");
//        }
//        if (capkey.indexOf("asr.cloud.freetalk.poi") != -1)
//        {
//        	recogConfig.addParam(AsrConfig.ResultConfig.PARAM_KEY_DOMAIN, "poi");
//        }
        
        //需要识别的音频文件，格式为pcm16k16bit单声道格式的
        String audioFile = "san_xia_shui_li_16k16.pcm";
        
        //非实时识别
        Recog(capkey, recogConfig, audioFile);
        
        //非实时识别
        RealtimeRecog(capkey,recogConfig,audioFile);
        
        
        if(grammarId.isValid()){
        	HciCloudAsr.hciAsrUnloadGrammar(grammarId);
        	ShowMessage("hciAsrUnloadGrammar");
        }
        //反初始化ASR
        HciCloudAsr.hciAsrRelease();
        ShowMessage("hciAsrRelease");

        return;
	}
	
}
