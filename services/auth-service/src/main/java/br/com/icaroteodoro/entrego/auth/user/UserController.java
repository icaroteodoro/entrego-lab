package br.com.icaroteodoro.entrego.auth.user;

import br.com.icaroteodoro.entrego.auth.user.dtos.CreateUserRequestDTO;
import br.com.icaroteodoro.entrego.auth.user.dtos.UserResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;


    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody CreateUserRequestDTO request) throws IllegalAccessException {
        return userService.create(request);
    }
}
