package com.example.projectname.module.users.mapper;

import com.example.projectname.module.users.dto.response.UserResponse;
import com.example.projectname.module.users.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserResponse toDto(User user);
}
