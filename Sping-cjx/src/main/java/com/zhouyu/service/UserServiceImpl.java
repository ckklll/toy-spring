package com.zhouyu.service;

import com.spring.*;

@Component("userService")
@Scope("singleton")
public class UserServiceImpl implements BeanNameAware, InitializingBean,UserService {
    @Autowired
    OrderService orderService;
    String beanName;
    @Override
    public void setBeanName(String name) {
        beanName = name;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("初始化");
    }

    @Loggable
    public void test() {
        System.out.println(orderService);
    }


}
