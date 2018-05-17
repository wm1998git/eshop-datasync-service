package com.wm.eshop.datasync.service;

import org.springframework.cloud.netflix.feign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import com.wm.eshop.datasync.service.fallback.EshopProductServiceFallback;

/** 使用Feign的客户端调用远程微服务接口，同时可作为微服务客户端的负载均衡 */
// "eshop-product-service"：远程RPC微服务名称。（这里应该使用Hystrix的降级策略：fallback = XXXServiceFallback.class），此注解会自动注入到Spring容器
@FeignClient(value = "eshop-product-service",fallback=EshopProductServiceFallback.class)
public interface EshopProductService {

	/** 远程调用eshop-product-service微服务对应的IP地址机器上的/category/findById接口服务，获取【商品-分类】原子数据的变更消息 */
	// 注解为其它工程的Control里的某个方法提供的RPC接口服务
	@RequestMapping(value = "/category/findById", method = RequestMethod.GET)
	String findCategoryById(@RequestParam(value = "id") Long id);

	/** 远程调用eshop-product-service微服务对应的IP地址机器上的/brand/findById接口服务，获取【商品-品牌】原子数据的变更消息 */
	@RequestMapping(value = "/brand/findById", method = RequestMethod.GET)
	String findBrandById(@RequestParam(value = "id") Long id);

	@RequestMapping(value = "/brand/findByIds", method = RequestMethod.GET)
	String findBrandByIds(@RequestParam(value = "ids") String ids);

	/** 远程调用eshop-product-service微服务对应的IP地址机器上的/product/findById接口服务，获取【商品-基本信息】原子数据的变更消息 */
	@RequestMapping(value = "/product/findById", method = RequestMethod.GET)
	String findProductById(@RequestParam(value = "id") Long id);

	/** 远程调用eshop-product-service微服务对应的IP地址机器上的/product-property/findById接口服务，获取【商品-属性】原子数据的变更消息 */
	@RequestMapping(value = "/product-property/findById", method = RequestMethod.GET)
	String findProductPropertyById(@RequestParam(value = "id") Long id);

	/** 远程调用eshop-product-service微服务对应的IP地址机器上的/product-specification/findById接口服务，获取【商品-规格】原子数据的变更消息 */
	@RequestMapping(value = "/product-specification/findById", method = RequestMethod.GET)
	String findProductSpecificationById(@RequestParam(value = "id") Long id);

	/** 远程调用eshop-product-service微服务对应的IP地址机器上的/product-intro/findById接口服务，获取【商品-介绍】原子数据的变更消息 */
	@RequestMapping(value = "/product-intro/findById", method = RequestMethod.GET)
	String findProductIntroById(@RequestParam(value = "id") Long id);

}