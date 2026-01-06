package com.zhouyu;

import com.spring.ApplicationContext;
import com.zhouyu.service.UserService;
import com.zhouyu.service.UserServiceImpl;

public class Test {
    public static void main(String[] args) {
        ApplicationContext applicationContext = new ApplicationContext(AppConfig.class);

        System.out.println(applicationContext.getBean("userService"));
        System.out.println(applicationContext.getBean("userService"));
        System.out.println(applicationContext.getBean("userService"));

        UserService userService = (UserService) applicationContext.getBean("userService");
        userService.test();

    }
}
