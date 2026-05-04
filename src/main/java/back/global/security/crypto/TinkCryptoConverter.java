package back.global.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JPA 엔티티의 특정 문자열 필드를 데이터베이스에 암호화하여 저장하고, 조회 시 복호화하는 커스텀 컨버터입니다.
 * <p>
 * 엔티티 필드에 {@code @Convert(converter = TinkCryptoConverter.class)}를 적용하면,
 * 데이터베이스에 Insert/Update 시 {@code convertToDatabaseColumn}이 호출되어 자동으로 암호화되고,
 * Select 시 {@code convertToEntityAttribute}가 호출되어 평문으로 변환됩니다.
 * 이를 통해 비즈니스 로직(Service) 계층에서는 암호화 처리를 의식하지 않고 평문으로 데이터를 다룰 수 있습니다.
 *
 * <p><b>상속 정보:</b><br>
 * {@code AttributeConverter<String, String>} 인터페이스를 구현합니다.
 *
 * <p><b>주요 생성자:</b><br>
 * 생성자는 롬복의 {@code @RequiredArgsConstructor}를 통해 생성되며, <br>
 * 암/복호화 실제 연산을 담당하는 {@code TinkCryptoUtil} 객체를 주입받습니다. <br>
 *
 * <p><b>빈 관리:</b><br>
 * {@code @Converter} 및 {@code @Component}로 등록되어 스프링 컨텍스트 및 JPA 라이프사이클에 의해 빈으로 관리됩니다.
 *
 * @author minhee
 * @see back.global.security.crypto.TinkCryptoUtil
 * @since 2026-04-30
 */

@Converter
@Component
@RequiredArgsConstructor
public class TinkCryptoConverter implements AttributeConverter<String, String> {

    private final TinkCryptoUtil cryptoUtil;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cryptoUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cryptoUtil.decrypt(dbData);
    }
}