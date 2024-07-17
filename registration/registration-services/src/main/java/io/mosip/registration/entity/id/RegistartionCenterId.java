package io.mosip.registration.entity.id;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import io.mosip.registration.entity.RegistrationCenter;
import lombok.Data;

/**
 * composite key for {@link RegistrationCenter}
 * 
 * @author Sreekar Chukka
 * @since 1.0.0
 */
@Embeddable
@Data
public class RegistartionCenterId implements Serializable {

	private static final long serialVersionUID = -7306845601917592413L;

	@Column(name = "id")
	private String id;

	@Column(name = "lang_code")
	private String langCode;

	/**
	 * @return the id
	 */
	public String getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return the langCode
	 */
	public String getLangCode() {
		return langCode;
	}

	/**
	 * @param langCode the langCode to set
	 */
	public void setLangCode(String langCode) {
		this.langCode = langCode;
	}

}
