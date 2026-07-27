-- GFBS Morphe 1.0 showcase
-- Open in-game with: /morpheui open gfbs_morphe:ui/showcase.lua

state.output = data.output or 63
state.armed = false
state.message = "Ready"

ui.root()
    :set("layout", "column")
    :set("align", "center")
    :set("justify", "center")

local output_text = ui.text({
    id = "output_text",
    height = 16,
    color = "#FF91F2FF",
    text_align = "right",
    vertical_align = "center"
})

local output_bar = ui.progress({
    id = "output_bar",
    height = 7,
    value = state.output,
    max = 100,
    fill_color = "#FF26C6DA"
})

local slider = ui.slider({
    id = "output_slider",
    height = 20,
    min = 0,
    max = 100,
    step = 1,
    value = state.output,
    on_change = function(event)
        state.output = event.value
        state.message = "Output setpoint changed"
    end
})

local armed = ui.checkbox({
    id = "armed",
    height = 20,
    label = "Arm remote control",
    checked = false,
    on_change = function(event)
        state.armed = event.value
        state.message = event.value and "Remote control armed" or "Remote control disarmed"
    end
})

local command = ui.input({
    id = "command",
    height = 23,
    placeholder = "Enter command and press Enter…",
    max_length = 80,
    on_submit = function(event)
        state.message = "Sent: " .. event.value
        ui.action("command", {
            command = event.value,
            output = state.output,
            armed = state.armed
        })
    end
})

local apply = ui.button({
    id = "apply",
    width = 92,
    height = 23,
    text = "APPLY",
    background = "#FF176B76",
    hover_background = "#FF218C99",
    pressed_background = "#FF104B53",
    tooltip = "Send the current setpoint to the server",
    on_click = function()
        state.message = "Setpoint applied"
        ui.action("apply", {
            output = state.output,
            armed = state.armed
        })
    end
})

local reset = ui.button({
    id = "reset",
    width = 76,
    height = 23,
    text = "RESET",
    on_click = function()
        state.output = 0
        slider.value = 0
        state.message = "Setpoint reset"
    end
})

local status = ui.text({
    id = "status",
    flex = 1,
    height = 18,
    color = "#FFAAB7C7",
    vertical_align = "center"
})

ui.bind(output_text, "text", function()
    return string.format("%d %%", state.output)
end)

ui.bind(output_bar, "value", function()
    return state.output
end)

ui.bind(status, "text", function()
    return state.message
end)

local card = ui.panel({
    id = "showcase",
    width = "82%",
    max_width = 460,
    height = 330,
    layout = "column",
    gap = 9,
    padding = 14,
    background = "#F019202B",
    border = "#FF3A4658",
    border_width = 1,
    radius = 6,
    clip = true,
    opacity = 0
}, {
    ui.panel({
        id = "header",
        width = "100%",
        height = 34,
        layout = "row",
        align = "center",
        gap = 8
    }, {
        ui.text({
            flex = 1,
            height = 24,
            text = "GFBS · MORPHE",
            font_size = 14,
            color = "#FFFFFFFF",
            vertical_align = "center"
        }),
        ui.text({
            width = 90,
            height = 20,
            text = "SCRIPT API 1",
            font_size = 8,
            color = "#FF65DDEA",
            text_align = "right",
            vertical_align = "center"
        })
    }),

    ui.panel({
        width = "100%",
        height = 1,
        background = "#FF344152"
    }),

    ui.scroll({
        id = "body",
        width = "100%",
        flex = 1,
        layout = "column",
        gap = 8,
        padding = "2 4 2 0"
    }, {
        ui.panel({
            width = "100%",
            height = 18,
            layout = "row",
            align = "center",
            gap = 8
        }, {
            ui.text({
                flex = 1,
                height = 16,
                text = "Reactor output",
                color = "#FFDDE7F2",
                vertical_align = "center"
            }),
            output_text
        }),
        output_bar,
        slider,
        armed,
        ui.text({
            width = "100%",
            height = 30,
            text = "Morphe documents are ordinary client resources. Resource packs may replace them without changing Java code.",
            color = "#FF8E9CAE",
            font_size = 8,
            wrap = true
        }),
        command
    }),

    ui.panel({
        id = "footer",
        width = "100%",
        height = 25,
        layout = "row",
        align = "center",
        gap = 7
    }, {
        status,
        reset,
        apply
    })
})

ui.mount(card)
ui.after(0.03, function()
    card:animate({ opacity = 1 }, 0.28, "ease_out")
end)
