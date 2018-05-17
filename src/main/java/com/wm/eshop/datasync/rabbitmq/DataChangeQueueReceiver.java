package com.wm.eshop.datasync.rabbitmq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.wm.eshop.datasync.service.EshopProductService;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * 获取各种原子数据的变更消息-充当RabbitMQ消费者<br/>
 * 数据同步服务核心思路：<br/>
 * （1）然后通过spring cloud fegion调用eshop-product-service服务的各种接口， 获取数据<br/>
 * （2）将原子数据在redis中进行增删改<br/>
 * （3）将维度数据变化消息写入rabbitmq中另外一个queue："aggr-data-change-queue"，供数据聚合服务来消费
 */
@Component
// 监听的队列:data-change-queue
@RabbitListener(queues = "data-change-queue")
public class DataChangeQueueReceiver {

	@Autowired
	private EshopProductService eshopProductService;
	@Autowired
	private JedisPool jedisPool;
	@Autowired
	private RabbitMQSender rabbitMQSender;

	/** 将维度数据变更消息采用set的方式，【在内存中先进行去重】 */
	private Set<String> dimDataChangeMessageSet = Collections.synchronizedSet(new HashSet<String>());

	/** 批量存储RabbitMQ队列中的原子数据变更消息 */
	private List<JSONObject> brandDataChangeMessageList = new ArrayList<JSONObject>();

	public DataChangeQueueReceiver() {
		new SendThread().start();
	}

	/** 构造器实现消费消息（消息来自于商品的分类、品牌、基本信息、属性、规格、介绍这些微服务的业务层） */
	@RabbitHandler
	public void process(String message) {

		// 对这个message进行解析
		JSONObject jsonObject = JSONObject.parseObject(message);

		// 先获取data_type这个JSON对象的属性值(【这里没有使用一些设计模式来优化】)
		String dataType = jsonObject.getString("data_type");

		if ("category".equals(dataType)) {
			processCategoryDataChangeMessage(jsonObject);
		} else if ("brand".equals(dataType)) {
			processBrandDataChangeMessage(jsonObject);
		} else if ("product".equals(dataType)) {
			processProductDataChangeMessage(jsonObject);
		} else if ("product_property".equals(dataType)) {
			processProductPropertyDataChangeMessage(jsonObject);
		} else if ("product_specification".equals(dataType)) {
			processProductSpecificationDataChangeMessage(jsonObject);
		} else if ("product_intro".equals(dataType)) {
			processProductIntroDataChangeMessage(jsonObject);
		}
	}

	/** 从MySQL获取【商品-分类】原子数据的变更消息后，写入Redis */
	private void processCategoryDataChangeMessage(JSONObject messageJSONObject) {
		Long id = messageJSONObject.getLong("id");
		String eventType = messageJSONObject.getString("event_type");

		if ("add".equals(eventType) || "update".equals(eventType)) {
			JSONObject dataJSONObject = JSONObject.parseObject(eshopProductService.findCategoryById(id));// 微服务：eshop-product-service的Controller提供的接口服务返回的是json串
			Jedis jedis = jedisPool.getResource();
			jedis.set("category_" + id, dataJSONObject.toJSONString());
		} else if ("delete".equals(eventType)) {
			Jedis jedis = jedisPool.getResource();
			jedis.del("category_" + id);
		}

		// dim_type：代表哪个维度
		// rabbitMQSender.send("aggr-data-change-queue", "{\"dim_type\": \"category\", \"id\": " + id + "}");
		dimDataChangeMessageSet.add("{\"dim_type\": \"category\", \"id\": " + id + "}");
	}

	/** 从MySQL获取【商品-品牌】原子数据的变更消息后，同步到Redis再向RabbitMQ的另一队列发送消息 */
	private void processBrandDataChangeMessage(JSONObject messageJSONObject) {

		Long id = messageJSONObject.getLong("id");
		String eventType = messageJSONObject.getString("event_type");

		if ("add".equals(eventType) || "update".equals(eventType)) {

			brandDataChangeMessageList.add(messageJSONObject);

			System.out.println("【将品牌数据放入内存list中】,list.size=" + brandDataChangeMessageList.size());

			if (brandDataChangeMessageList.size() >= 2) {
				System.out.println("【将品牌数据内存list大小大于等于2，开始执行批量调用】");

				String ids = "";

				for (int i = 0; i < brandDataChangeMessageList.size(); i++) {
					ids += brandDataChangeMessageList.get(i).getLong("id");
					if (i < brandDataChangeMessageList.size() - 1) {
						ids += ",";
					}
				}

				System.out.println("【品牌数据ids生成】ids=" + ids);

				JSONArray brandJSONArray = JSONArray.parseArray(eshopProductService.findBrandByIds(ids));// 【优化：改为批量查询依赖的服务】

				System.out.println("【通过批量调用获取到品牌数据】jsonArray=" + brandJSONArray.toJSONString());

				JSONObject dataJSONObject = null;
				Jedis jedis = jedisPool.getResource();
				Long brandId = 0L;

				for (int i = 0; i < brandJSONArray.size(); i++) {
					dataJSONObject = brandJSONArray.getJSONObject(i);

					brandId = dataJSONObject.getLong("id");
					jedis.set("brand_" + brandId, dataJSONObject.toJSONString());

					System.out.println("【将品牌数据写入redis】brandId=" + brandId);

					dimDataChangeMessageSet.add("{\"dim_type\": \"brand\", \"id\": " + brandId + "}");

					System.out.println("【将品牌数据写入内存去重set中】brandId=" + brandId);
				}

				brandDataChangeMessageList.clear();

			}
		} else if ("delete".equals(eventType)) {
			Jedis jedis = jedisPool.getResource();
			jedis.del("brand_" + id);
			dimDataChangeMessageSet.add("{\"dim_type\": \"brand\", \"id\": " + id + "}");
		}
	}

	/** 从MySQL获取【商品-基本信息】原子数据的变更消息后，写入Redis */
	private void processProductDataChangeMessage(JSONObject messageJSONObject) {
		Long id = messageJSONObject.getLong("id");
		String eventType = messageJSONObject.getString("event_type");

		if ("add".equals(eventType) || "update".equals(eventType)) {
			JSONObject dataJSONObject = JSONObject.parseObject(eshopProductService.findProductById(id));
			Jedis jedis = jedisPool.getResource();
			jedis.set("product_" + id, dataJSONObject.toJSONString());
		} else if ("delete".equals(eventType)) {
			Jedis jedis = jedisPool.getResource();
			jedis.del("product_" + id);
		}

		// rabbitMQSender.send("aggr-data-change-queue", "{\"dim_type\": \"product\", \"id\": " + id + "}");
		dimDataChangeMessageSet.add("{\"dim_type\": \"product\", \"id\": " + id + "}");
	}

	/** 从MySQL获取【商品-属性】原子数据的变更消息后，写入Redis */
	private void processProductPropertyDataChangeMessage(JSONObject messageJSONObject) {
		Long id = messageJSONObject.getLong("id");
		Long productId = messageJSONObject.getLong("product_id");
		String eventType = messageJSONObject.getString("event_type");

		if ("add".equals(eventType) || "update".equals(eventType)) {
			JSONObject dataJSONObject = JSONObject.parseObject(eshopProductService.findProductPropertyById(id));
			Jedis jedis = jedisPool.getResource();
			jedis.set("product_property_" + productId, dataJSONObject.toJSONString());// 因为后边是按productId把数据聚合起来的
		} else if ("delete".equals(eventType)) {
			Jedis jedis = jedisPool.getResource();
			jedis.del("product_property_" + productId);
		}

		// rabbitMQSender.send("aggr-data-change-queue", "{\"dim_type\": \"product\", \"id\": " + productId + "}");
		dimDataChangeMessageSet.add("{\"dim_type\": \"product\", \"id\": " + productId + "}");
	}

	/** 从MySQL获取【商品-规格】原子数据的变更消息后，写入Redis */
	private void processProductSpecificationDataChangeMessage(JSONObject messageJSONObject) {
		Long id = messageJSONObject.getLong("id");
		Long productId = messageJSONObject.getLong("product_id");
		String eventType = messageJSONObject.getString("event_type");

		if ("add".equals(eventType) || "update".equals(eventType)) {
			JSONObject dataJSONObject = JSONObject.parseObject(eshopProductService.findProductSpecificationById(id));
			Jedis jedis = jedisPool.getResource();
			jedis.set("product_specification_" + productId, dataJSONObject.toJSONString());// 因为后边是按productId把数据聚合起来的
		} else if ("delete".equals(eventType)) {
			Jedis jedis = jedisPool.getResource();
			jedis.del("product_specification_" + productId);
		}

		// rabbitMQSender.send("aggr-data-change-queue", "{\"dim_type\": \"product\", \"id\": " + productId + "}");
		dimDataChangeMessageSet.add("{\"dim_type\": \"product\", \"id\": " + productId + "}");
	}

	/** 从MySQL获取【商品-介绍】原子数据的变更消息后，写入Redis */
	private void processProductIntroDataChangeMessage(JSONObject messageJSONObject) {
		Long id = messageJSONObject.getLong("id");
		Long productId = messageJSONObject.getLong("product_id");
		String eventType = messageJSONObject.getString("event_type");

		if ("add".equals(eventType) || "update".equals(eventType)) {
			JSONObject dataJSONObject = JSONObject.parseObject(eshopProductService.findProductIntroById(id));
			Jedis jedis = jedisPool.getResource();
			jedis.set("product_intro_" + productId, dataJSONObject.toJSONString());// 因为后边是按productId把数据聚合起来的
		} else if ("delete".equals(eventType)) {
			Jedis jedis = jedisPool.getResource();
			jedis.del("product_intro_" + productId);
		}

		// rabbitMQSender.send("aggr-data-change-queue", "{\"dim_type\": \"product_intro\", \"id\": " + productId + "}");
		dimDataChangeMessageSet.add("{\"dim_type\": \"product_intro\", \"id\": " + productId + "}");
	}

	private class SendThread extends Thread {

		@Override
		public void run() {
			while (true) {
				if (!dimDataChangeMessageSet.isEmpty()) {
					for (String message : dimDataChangeMessageSet) {
						rabbitMQSender.send("aggr-data-change-queue", message);
					}
					dimDataChangeMessageSet.clear();
				}
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}
}