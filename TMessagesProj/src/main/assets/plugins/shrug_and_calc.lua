-- id: shrug_and_calc
-- name: Shrug & Mini Calc
-- version: 1.0.0
-- author: @dkramochka
-- description: Замінює /shrug на ¯\_(ツ)_/¯ та обчислює вирази виду =2+2*2 прямо в повідомленнях.
-- icon: msg_bot

function on_plugin_load()
    client.log("Shrug & Calc Lua plugin loaded!")
end

function on_message_send(text, chat_id)
    if not text then return text end

    -- Заміна /shrug на класичний смайлик
    if text == "/shrug" then
        return "¯\\_(ツ)_/¯"
    end
    if text:find("/shrug") then
        text = text:gsub("/shrug", "¯\\_(ツ)_/¯")
    end

    -- Заміна /flip на (╯°□°)╯︵ ┻━┻
    if text == "/flip" then
        return "(╯°□°)╯︵ ┻━┻"
    end

    -- Міні-калькулятор для виразів, що починаються з '='
    if text:sub(1, 1) == "=" and #text > 1 then
        local expr = text:sub(2)
        -- Дозволяємо лише безпечні арифметичні символи
        if expr:match("^[%d%s%+%-%*%/%%%^%(%)%.]+$") then
            local chunk = load("return " .. expr)
            if chunk then
                local ok, result = pcall(chunk)
                if ok and result ~= nil then
                    return expr:gsub("^%s*(.-)%s*$", "%1") .. " = " .. tostring(result)
                end
            end
        end
    end

    return text
end

function on_command(cmd, args, chat_id)
    if cmd == "shrug" then
        return "¯\\_(ツ)_/¯"
    elseif cmd == "flip" then
        return "(╯°□°)╯︵ ┻━┻"
    elseif cmd == "calc" and args and #args > 0 then
        if args:match("^[%d%s%+%-%*%/%%%^%(%)%.]+$") then
            local chunk = load("return " .. args)
            if chunk then
                local ok, result = pcall(chunk)
                if ok and result ~= nil then
                    return args .. " = " .. tostring(result)
                end
            end
        end
    end
    return nil
end
