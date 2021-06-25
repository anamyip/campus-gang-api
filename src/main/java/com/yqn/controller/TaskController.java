package com.yqn.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yqn.pojo.Task;
import com.yqn.pojo.User;
import com.yqn.service.TaskService;
import com.yqn.common.tools.MessageTools;
import com.yqn.common.tools.PocketMoney;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/**
 * @author Qingnan Ye
 */
@RestController
@RequestMapping("/task")
@Slf4j
public class TaskController {
    @Autowired
    private TaskService taskService;
    @Autowired
    private MessageTools message;
    @Autowired
    private PocketMoney money;

    // 获取当前登录user所在学校的任务
    @GetMapping
    public Map<String, Object> tasks(HttpSession session) {
        User user = (User) session.getAttribute("user");
        // log.info("user: {}", user);
        if (user != null) {
            QueryWrapper<Task> wrapper = new QueryWrapper<>();
            wrapper.eq("user_school_id", user.getSchool().getId());
            return message.message(true, taskService.list(wrapper));
        }
        List<Task> tasks = taskService.list();
        return message.message(true, tasks);
    }

    // 根据id获取task
    @GetMapping("/{id}")
    public Map<String, Object> task(@PathVariable String id) {
        Task task = taskService.getById(id);
        return message.message(true, task);
    }

    // 当前登录User, 已发布的task
    @GetMapping("/published")
    public Map<String, Object> published(HttpSession session) {
        return message.message(true, publishAndAcceptMethods(session, "publish_user_id"));
    }

    // 当前登录User, 已接受的task
    @GetMapping("/accepted")
    public Map<String, Object> accepted(HttpSession session) {
        return message.message(true, publishAndAcceptMethods(session, "accept_user_id"));
    }

    // 获取发布和接受的task
    public List<Task> publishAndAcceptMethods(HttpSession session, String field) {
        User user = (User) session.getAttribute("user");
        QueryWrapper<Task> wrapper = new QueryWrapper<>();
        wrapper.eq(field, user.getId());
        return taskService.list(wrapper);
    }

    // 发布新task
    @PostMapping
    public Map<String, Object> saveTask(Task task, HttpSession session) {
        User user = (User) session.getAttribute("user");

        if (user.getBalance() >= task.getReward()) {
            task.setPublishId(user.getId());
            task.setSchoolId(user.getSchool().getId());
            boolean b = taskService.save(task);
            if (b) {
                money.transfer("balance=balance-", task.getReward(), user.getStudentId());
            }
            return message.message(true, "发布任务成功");
        } else {
            return message.message(false, "余额不足");
        }
    }

    // 发布人取消task
    @DeleteMapping("/{id}")
    public Map<String, Object> delTask(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        Task task = taskService.getById(id);
        System.out.println(task);
        if (task != null) {
            taskService.removeById(id);
            money.transfer("balance=balance+", task.getReward(), user.getStudentId());
        }
        return message.message(true, "取消任务成功");
    }

    // 接单人取消task
    @PutMapping("/takerCancel/{id}")
    public Map<String, Object> takerCancel(@PathVariable Long id) {
        UpdateWrapper<Task> wrapper = new UpdateWrapper<>();
        wrapper.setSql("accept_user_id=null")
                .setSql("order_time=null")
                .setSql("state=0")
                .eq("id", id);

        boolean update = taskService.update(wrapper);
        if (update) {
            return message.message(true, "取消任务成功");
        }
        return message.message(false, "取消任务失败");
    }

    // 接单人接受task
    @PutMapping("/takerAccept/{id}")
    public Map<String, Object> takerAccept(@PathVariable Long id, HttpSession session) {
        User user = (User) session.getAttribute("user");
        UpdateWrapper<Task> wrapper = new UpdateWrapper<>();
        wrapper.setSql("accept_user_id=" + user.getId())
                .setSql("order_time=now()")
                .setSql("state=1")
                .eq("id", id);
        boolean b = taskService.update(wrapper);
        if (b) {
            return message.message(true, "接收任务成功");
        }
        return message.message(false, "接受任务失败");
    }

    // 完成task
    @PutMapping("/{id}")
    public Map<String, Object> missionCompleted(@PathVariable Long id) {
        UpdateWrapper<Task> wrapper = new UpdateWrapper<>();
        wrapper.setSql("end_time=now()")
                .setSql("state=2")
                .eq("id", id);
        boolean b = taskService.update(wrapper);
        if (b) {
            Task task = taskService.getById(id);
            if (task != null) {
                money.transfer("balance=balance+", task.getReward(), task.getAccept().getStudentId());
            }
            return message.message(true, "任务完成");
        }
        return message.message(false, "任务完成失败");
    }

    // 修改task
    @PutMapping("/edit")
    public Map<String, Object> editTask(Task task, HttpSession session) {
        User user = (User) session.getAttribute("user");
        Task byId = taskService.getById(task.getId());
        if (user.getId().equals(byId.getPublish().getId())) {
            boolean update = taskService.updateById(task);
            if (update) {
                return message.message(true, "编辑任务成功");
            }
            return message.message(false, "编辑任务失败");
        }
        return message.message(false, "无权编辑该任务");
    }

}