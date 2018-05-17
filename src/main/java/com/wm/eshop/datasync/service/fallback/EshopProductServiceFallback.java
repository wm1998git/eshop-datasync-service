package com.wm.eshop.datasync.service.fallback;

import org.springframework.stereotype.Component;
import com.wm.eshop.datasync.service.EshopProductService;

/** Hystrix熔断，降级类 */
@Component
public class EshopProductServiceFallback implements EshopProductService {

	@Override
	public String findCategoryById(Long id) {
		return null;
	}

	@Override
	public String findBrandById(Long id) {
		return null;
	}

	@Override
	public String findBrandByIds(String ids) {
		return null;
	}

	@Override
	public String findProductById(Long id) {
		return null;
	}

	@Override
	public String findProductPropertyById(Long id) {
		return null;
	}

	@Override
	public String findProductSpecificationById(Long id) {
		return null;
	}

	@Override
	public String findProductIntroById(Long id) {
		return null;
	}

}
