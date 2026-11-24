package vn.farmcontrol.command;

import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.farmcontrol.auth.CurrentUser;
import vn.farmcontrol.command.CommandDtos.*;
import vn.farmcontrol.plc.CommandType;

@RestController
@RequestMapping("/api/commands")
public class CommandController {

    private final CommandService commands;

    public CommandController(CommandService commands) {
        this.commands = commands;
    }

    public record CommandTypeView(CommandType type, String name, int plcCode, List<String> devices) {
    }

    /** Danh sách lệnh hỗ trợ (để Flutter dựng nút điều khiển). */
    @GetMapping("/types")
    public List<CommandTypeView> types() {
        return Arrays.stream(CommandType.values())
                .map(t -> new CommandTypeView(t, t.displayName(), t.plcCode(),
                        t.devices().stream().map(Enum::name).sorted().toList()))
                .toList();
    }

    /**
     * Gửi lệnh. Trả về 202 với lệnh ở trạng thái SENT; kết quả cuối cùng (EXECUTED/REJECTED/...)
     * được đẩy qua WebSocket /topic/commands hoặc lấy lại bằng GET /api/commands/{id}.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER','OPERATOR')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandResponse create(@Valid @RequestBody CreateCommandRequest req, CurrentUser user) {
        return commands.submit(req.type(), user);
    }

    @GetMapping("/{id}")
    public CommandResponse get(@PathVariable Long id) {
        return CommandResponse.of(commands.get(id));
    }
}
