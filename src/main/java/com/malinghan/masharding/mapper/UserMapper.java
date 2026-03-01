package com.malinghan.masharding.mapper;

import com.malinghan.masharding.model.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("select * from user where id = #{id}")
    User findById(@Param("id") int id);

    @Insert("insert into user(id, name, age) values(#{id}, #{name}, #{age})")
    void insert(User user);

    @Select("select * from user")
    List<User> findAll();
}
