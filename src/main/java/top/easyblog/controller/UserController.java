package top.easyblog.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import top.easyblog.entity.User;
import top.easyblog.service.UserService;

/**
 * @author ：huangxin
 * @modified ：
 * @since ：2020/06/10 19:52
 */
@Controller
public class UserController {

    @Autowired
    private UserService userService;

    @ResponseBody
    @GetMapping(value = "/user")
    public User searchUser(@RequestParam(value = "id") int id) {
        return userService.getUserById(id);
    }
}
