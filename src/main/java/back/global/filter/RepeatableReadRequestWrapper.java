package back.global.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * HTTP 요청의 Body(InputStream)를 여러 번 읽을 수 있도록 바이트 배열로 캐싱하는 Wrapper 클래스.
 * 서명 검증 필터 등에서 원본 데이터를 미리 읽어야 할 때 사용됩니다.
 */

public final class RepeatableReadRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    /**
     * 생성자: 요청이 들어올 때 InputStream을 모두 읽어서 바이트 배열로 복사해 둡니다.
     *
     * @param request 원본 HttpServletRequest
     * @throws IOException 스트림 읽기 실패 시 발생
     */
    public RepeatableReadRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /**
     * 필터 체인이나 컨트롤러가 InputStream을 요구할 때마다,
     * 캐싱된 바이트 배열을 바탕으로 새로운 ServletInputStream을 생성하여 반환합니다.
     */
    @Override
    public ServletInputStream getInputStream() {
        return new CachedServletInputStream(this.cachedBody);
    }

    /**
     * 필터 체인이나 컨트롤러가 BufferedReader를 요구할 때마다,
     * 캐싱된 바이트 배열을 바탕으로 새로운 BufferedReader를 생성하여 반환합니다.
     */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.cachedBody), StandardCharsets.UTF_8));
    }

    /**
     * HMAC 서명 검증 등 원시(raw) 바이트 배열 자체가 필요할 때 사용할 수 있는 유틸리티 메서드입니다.
     */
    public byte[] getCachedBody() {
        return Arrays.copyOf(this.cachedBody, this.cachedBody.length);
    }

    /**
     * 캐싱된 바이트 배열을 감싸서 서블릿이 요구하는 ServletInputStream 스펙을 맞추는 내부 클래스.
     */
    private static class CachedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        public CachedServletInputStream(byte[] contents) {
            this.buffer = new ByteArrayInputStream(contents);
        }

        @Override
        public int read() {
            return buffer.read();
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            throw new UnsupportedOperationException("ReadListener is not supported in CachedServletInputStream");
        }
    }
}