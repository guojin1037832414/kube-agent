package com.atlas.intent.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * ONNX 模型文件下载 + 本地路径解析器。
 *
 * <p>三级查找策略：</p>
 * <ol>
 *   <li><b>本地路径</b>：{@code ~/.atlas/models/all-MiniLM/} 下查找 model.onnx</li>
 *   <li><b>classpath</b>：jar 包内 {@code /models/all-MiniLM/} 查找（测试/离线环境）</li>
 *   <li><b> HuggingFace Hub 下载</b>：首次启动自动下载到本地路径</li>
 * </ol>
 *
 * <p>下载目标文件（共 2 个）：{@code model.onnx}（~90MB）+ {@code tokenizer.json}（~0.5MB）</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
public final class ModelDownloader {

    private static final Logger log = LoggerFactory.getLogger(ModelDownloader.class);
    private static final String MODEL_FILE = "model.onnx";
    private static final String TOKENIZER_FILE = "tokenizer.json";

    private ModelDownloader() {}

    /**
     * 解析并返回可用的 {@code model.onnx} 绝对路径。
     *
     * @param config Embedding 配置
     * @return 可加载的 ONNX 模型文件路径
     * @throws IllegalStateException 三级查找全部失败
     */
    public static Path resolveModelPath(EmbeddingConfig config) {
        return resolve(MODEL_FILE, config);
    }

    /**
     * 解析并返回可用的 {@code tokenizer.json} 绝对路径。
     *
     * @param config Embedding 配置
     * @return 可加载的 tokenizer 配置文件路径
     * @throws IllegalStateException 三级查找全部失败
     */
    public static Path resolveTokenizerPath(EmbeddingConfig config) {
        return resolve(TOKENIZER_FILE, config);
    }

    private static Path resolve(String fileName, EmbeddingConfig config) {
        // L1: 本地磁盘
        Path localDir = Path.of(config.getModelPath());
        Path localFile = localDir.resolve(fileName);
        if (Files.exists(localFile)) {
            log.info("[ModelDownloader] {} 命中本地路径: {}", fileName, localFile);
            return localFile.toAbsolutePath();
        }

        // L2: classpath（开发测试时用）
        URL classpathUrl = ModelDownloader.class.getResource("/models/all-MiniLM/" + fileName);
        if (classpathUrl != null) {
            try {
                Path classpathFile = Path.of(classpathUrl.toURI());
                log.info("[ModelDownloader] {} 命中 classpath: {}", fileName, classpathFile);
                return classpathFile.toAbsolutePath();
            } catch (Exception e) {
                log.warn("[ModelDownloader] classpath 解析失败: {}", e.getMessage());
            }
        }

        // L3: HuggingFace 自动下载
        log.info("[ModelDownloader] {} 本地未找到，尝试从 HuggingFace 下载...", fileName);
        Path downloaded = downloadFromHuggingFace(fileName, localDir, config);
        if (downloaded != null) {
            return downloaded.toAbsolutePath();
        }

        throw new IllegalStateException(
            "无法加载模型文件: " + fileName +
            "。请手动下载 sentence-transformers/all-MiniLM-L6-v2 到 " + localDir
        );
    }

    private static Path downloadFromHuggingFace(String fileName, Path localDir, EmbeddingConfig config) {
        // ── 策略1: 根目录 (如 model.onnx) ──
        Path result = tryDownload(fileName, localDir, config);
        if (result != null) return result;

        // ── 策略2: onnx/ 子目录 (如 onnx/model.onnx) ──
        if ("model.onnx".equals(fileName) || "tokenizer.json".equals(fileName)) {
            result = tryDownload("onnx/" + fileName, localDir.resolve("onnx"), config);
            if (result != null) return result;
        }

        return null;
    }

    private static Path tryDownload(String relativePath, Path targetDir, EmbeddingConfig config) {
        try {
            Files.createDirectories(targetDir);
            String urlStr = String.format(
                "https://huggingface.co/%s/resolve/main/%s",
                config.getModelId(), relativePath
            );
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(Math.max(5000, config.getDownloadTimeoutSeconds() * 1000));
            conn.setReadTimeout(Math.max(5000, config.getDownloadTimeoutSeconds() * 1000));
            conn.setRequestProperty("User-Agent", "Atlas-Agent/3.1");

            int code = conn.getResponseCode();
            if (code != 200) {
                log.debug("[ModelDownloader] {} HTTP {}: {}", relativePath, code, urlStr);
                return null;
            }

            Path target = targetDir.resolve(relativePath.contains("/")
                ? relativePath.substring(relativePath.lastIndexOf('/') + 1)
                : relativePath);
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[ModelDownloader] {} 下载完成: {} ({} bytes)",
                relativePath, target, Files.size(target));
            return target;
        } catch (IOException e) {
            log.debug("[ModelDownloader] {} 下载异常: {}", relativePath, e.getMessage());
            return null;
        }
    }
}
