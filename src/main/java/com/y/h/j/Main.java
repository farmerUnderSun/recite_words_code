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
        String content = equipContent(5, 300, words);
        System.out.println(content);
        System.out.println("--------------------------------------");
        Map<String, Object> result = new HashMap<>();
        result.put("role", "user");
        result.put("content", content);
        return result;
    }

    private static String equipContent(int storyAmount, int wordsAmountInOneStory, String words) {
        int requirementNumber = 1;
        StringBuffer stringBuffer = new StringBuffer();
        stringBuffer.append("你是一个英文作家，我将给你提供一些单词或短语，请用这些单词或短语编写").append(storyAmount).append("个小故事，具体要求如下，请务必严格遵守。");
        stringBuffer.append("我提供的单词如下【").append(words).append("】。");
        stringBuffer.append("要求").append(requirementNumber++).append(": ").append("用英文编写");
        stringBuffer.append("要求").append(requirementNumber++).append(": ").append("每个故事的单词数量大约为").append(wordsAmountInOneStory).append("个;");
        stringBuffer.append("要求").append(requirementNumber++).append(": ").append("每个故事的题材都不一样;");
        stringBuffer.append("要求").append(requirementNumber++).append(": ").append("在编写这几个故事的过程中，尽量包含我提供的所有单词或短语，但不要求每个故事都包含所有的单词或短语，只要这几个故事一共包含这些单词或短语即可;");
        stringBuffer.append("要求").append(requirementNumber++).append(": ").append("严格遵守英语语法，多使用状语从句、主语从句、宾语从句、表语从句、同位语从句、定语从句等从句");
        stringBuffer.append("要求").append(requirementNumber++).append(": ").append("故事情节合理，语句通顺");
        return stringBuffer.toString();
    }
}