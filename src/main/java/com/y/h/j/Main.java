package com.y.h.j;

import com.fasterxml.jackson.databind.JsonNode;
import com.yonyou.loc.base.util.*;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Main {
    private static final String BASE_PATH = "/Users/yanghuijun/CProjects/recite_words";
    private static final int USED_FILE_COUNT_OF_RANDOM = 10;
    private static final int USED_FILE_COUNT_OF_FIX = 5;

    public static void main(String[] args) {
        File basePathFile = new File(BASE_PATH);
        if (!basePathFile.isDirectory()) {
            return;
        }
        File[] files = basePathFile.listFiles();
        if (files == null) {
            return;
        }
        List<File> usedFiles = new ArrayList<>();
        // 过滤掉不符合规范名称的文件
        List<File> validFiles = new ArrayList<>();
        for (File file : files) {
            if (!targetFileName(file.getName())) {
                continue;
            }
            validFiles.add(file);
        }
        // 按照时间从小到大排序
        for (int i = validFiles.size() - 1; i >= 0; i--) {
            for (int j = 0; j < i; j++) {
                File leftFile = validFiles.get(j);
                File rightFile = validFiles.get(j + 1);
                String leftFileName = leftFile.getName();
                String rightFileName = rightFile.getName();
                int timeInLeftFileName = Integer.parseInt(leftFileName.substring(5, 12));
                int timeInRightFileName = Integer.parseInt(rightFileName.substring(5, 12));
                if (timeInLeftFileName > timeInRightFileName) {
                    File temp = leftFile;
                    validFiles.set(j, rightFile);
                    validFiles.set(j + 1, temp);
                }
            }
        }
        // 取出最新的几个
        int originalValidFileListSize = validFiles.size();
        for (int i = 1; i <= USED_FILE_COUNT_OF_FIX; i++) {
            int index = originalValidFileListSize - i;
            if (index < 0) {
                break;
            }
            File removed = validFiles.remove(index);
            System.out.println("***用到的文件:" + removed.getName());
            usedFiles.add(removed);
        }
        System.out.println();
        // 随机取出几个
        List<Integer> randomNumbersInRange = NumberUtils.getRandomNumbersInRange(1, validFiles.size(), USED_FILE_COUNT_OF_RANDOM);
        int i = 0;
        for (File file : validFiles) {
            if (!targetFileName(file.getName())) {
                continue;
            }
            i++;
            if (!randomNumbersInRange.contains(i)) {
                System.out.println("!!!没用到的文件:" + file.getName());
                continue;
            }
            System.out.println("***用到的文件:" + file.getName());
            usedFiles.add(file);
        }
        System.out.println("--------------------------------------");
        StringBuffer words = new StringBuffer();
        for (File file : usedFiles) {
            List<String> lineList = StreamUtils.readFileToLineList(file, "UTF-8", 10);
            for (String line : lineList) {
                words.append(line).append(", ");
            }
        }
        System.out.println(words);
        System.out.println("--------------------------------------");

        OkHttpClient client = new OkHttpClient();
        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        String jsonBody = equipRequestBody(words.toString());
        RequestBody body = RequestBody.create(jsonBody, JSON);

        Request request = new Request.Builder()
                .url("https://api.deepseek.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer sk-95e1c53f51d84ec1af0e7ef471434bf2")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    System.err.println("请求失败: " + response.code());
                    return;
                }

                try (ResponseBody responseBody = response.body()) {
                    if (responseBody != null) {
                        processStream(responseBody.source());
                    }
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                System.err.println("请求异常: " + e.getMessage());
            }
        });

        // 等待响应完成
        try {
            Thread.sleep(30000); // 根据实际情况调整
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void processStream(okio.BufferedSource source) throws IOException {
        while (!source.exhausted()) {
            String line = source.readUtf8Line();
            if (line == null) continue;

            if (line.startsWith("data: ")) {
                String data = line.substring(6);
                if (data.equals("[DONE]")) {
                    System.out.println("\n\n流式传输完成");
                    break;
                }

                try {
                    JsonNode node = ObjectMapperUtils.getObjectMapper().readTree(data);
                    JsonNode choices = node.path("choices");
                    if (choices.isArray() && choices.size() > 0) {
                        JsonNode delta = choices.get(0).path("delta");
                        if (delta.has("content")) {
                            String content = delta.get("content").asText();
                            System.out.print(content);
                        }
                    }
                } catch (Exception e) {
                    // 忽略JSON解析错误
                }
            }
        }
    }

    private static boolean targetFileName(String fileName) {
        if (fileName.startsWith("word202")) {
            return true;
        }
        return false;
    }

    private static String equipRequestBody(String words) {
        Map<String, Object> result = new HashMap<>();
        result.put("model", "deepseek-chat");
        result.put("messages", ListUtils.newArrayList(equipRequestBodyMessage(words)));
        result.put("temperature", 0.1);
        result.put("stream", true);
        return ObjectMapperUtils.objectToJson(result);
    }

    private static Map<String, Object> equipRequestBodyMessage(String words) {
        String content = "请用一下单词、短语等信息编写一个小故事，500字左右。要求：用英文编写，语句通顺， 语法准确，尽量少用不在这里的词汇， 每个词汇可能有多个含义，只选择一两个最常用的即可， 如果文档中有错误单词，纠正后再使用，故事要合乎常理，每个字都不用双星号加粗，最好有趣一点。单词、短语等信息如下：" + words;
        System.out.println(content);
        System.out.println("--------------------------------------");
        Map<String, Object> result = new HashMap<>();
        result.put("role", "user");
        result.put("content", content);
        return result;
    }
}